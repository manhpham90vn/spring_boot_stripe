package com.manhpham.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource-server dùng chung cho mọi service servlet (Catalog, Order, Inventory,
 * Payment, Ticket...). Mỗi request mang Bearer JWT được verify CỤC BỘ bằng public
 * key lấy từ JWKS của Auth (cache lại, không gọi Auth mỗi request) — danh tính
 * đến từ token đã xác thực mật mã, không tin bất kỳ header nào do client/gateway
 * gắn vào, nên không thể giả mạo dù gọi thẳng service trong cluster.
 *
 * <p>Service chỉ cần: thêm dependency {@code common-security}, đặt
 * {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri} + {@code app.security.issuer},
 * và KHAI LUẬT PHÂN QUYỀN bằng {@code app.security.rules[...]} (xem {@link ResourceServerProperties}).
 * Không phải viết SecurityConfig riêng nữa. Muốn ghi đè hoàn toàn thì tự khai
 * {@code SecurityFilterChain}/{@code JwtDecoder} của mình — các bean dưới đây sẽ tự nhường.
 */
@AutoConfiguration
@ConditionalOnClass({SecurityFilterChain.class, JwtDecoder.class})
@ConditionalOnWebApplication(type = Type.SERVLET)
@EnableConfigurationProperties(ResourceServerProperties.class)
public class ResourceServerAutoConfiguration {

    /** Path hạ tầng luôn mở: health probe, scrape Prometheus, swagger. */
    private static final String[] INFRA_PUBLIC = {
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    /**
     * Quy ước API NỘI BỘ (service↔service). Để permitAll ở tầng app vì lời gọi nội bộ
     * không mang JWT người dùng; rào chắn THẬT là ở tầng mạng — gateway KHÔNG khai route
     * cho {@code /internal/**} (không với tới từ ngoài) + K8s NetworkPolicy chỉ cho
     * service trong cluster gọi nhau. Xem docs/standards/SECURITY-ACCESS-CONTROL.md.
     */
    private static final String INTERNAL_PREFIX = "/internal/**";

    /**
     * Tên claim chứa danh sách role trong JWT, và prefix mà Spring Security quy ước
     * cho authority ({@code USER} → {@code ROLE_USER}). Quy ước này phải KHỚP với phía
     * phát token (Auth) và với gateway (apigateway/SecurityConfig) — sửa đây nhớ sửa cả đó.
     */
    private static final String ROLES_CLAIM = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    @Bean
    @ConditionalOnMissingBean
    public SecurityFilterChain resourceServerSecurityFilterChain(HttpSecurity http,
            ResourceServerProperties props) throws Exception {
        http
                // API stateless: không session, không CSRF (CSRF chỉ có ý nghĩa với cookie/session).
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Dựng bảng phân quyền từ KHAI BÁO trong properties (xem ResourceServerProperties).
                // Thứ tự đăng ký = thứ tự xét (khớp trước thắng): built-in trước, rồi rules, cuối là anyRequest.
                .authorizeHttpRequests(auth -> {
                    // 1) Hạ tầng (health/metrics/swagger) + API nội bộ (rào ở tầng mạng) → luôn mở.
                    auth.requestMatchers(INFRA_PUBLIC).permitAll();
                    auth.requestMatchers(INTERNAL_PREFIX).permitAll();
                    // 2) Lối tắt public-paths đơn giản (permitAll, mọi method).
                    for (String p : props.getPublicPaths()) {
                        auth.requestMatchers(p).permitAll();
                    }
                    // 3) Luật chi tiết (có thể kèm method + role), áp theo đúng thứ tự khai báo.
                    for (ResourceServerProperties.Rule rule : props.getRules()) {
                        var matcher = (rule.getMethod() == null || rule.getMethod().isBlank())
                                ? auth.requestMatchers(rule.getPattern())
                                : auth.requestMatchers(HttpMethod.valueOf(rule.getMethod().toUpperCase()),
                                        rule.getPattern());
                        switch (rule.effectiveAccess()) {
                            case PERMIT_ALL -> matcher.permitAll();
                            case HAS_ROLE -> matcher.hasRole(rule.getRole());
                            case AUTHENTICATED -> matcher.authenticated();
                        }
                    }
                    // 4) Còn lại: bắt buộc JWT hợp lệ (kể cả khi bị gọi thẳng trong cluster).
                    auth.anyRequest().authenticated();
                })
                // Bật resource-server: verify Bearer JWT bằng jwtDecoder bên dưới, rồi
                // chuyển claim "roles" thành authority để @PreAuthorize/hasRole hoạt động.
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(rolesAuthenticationConverter())));
        return http.build();
    }

    /**
     * Decoder verify JWT CỤC BỘ tại mỗi service. Một dòng {@code withJwkSetUri(...)}
     * của Spring Security nhìn như "magic" nhưng nó tự động làm các bước sau:
     *
     * <ol>
     *   <li>Đọc header JWT, lấy {@code kid} (id của key đã ký token).</li>
     *   <li>Tải JWKS (tập public key) từ {@code jwk-set-uri} của Auth và CACHE lại
     *       → KHÔNG gọi Auth cho từng request (yêu cầu cốt lõi của kiến trúc này).</li>
     *   <li>Chọn đúng public key theo {@code kid} rồi verify CHỮ KÝ RS256.</li>
     *   <li>{@code JwtValidators.createDefault()}: kiểm tra hạn ({@code exp}) và thời
     *       điểm hiệu lực ({@code nbf}/{@code iat}).</li>
     *   <li>{@code JwtIssuerValidator}: kiểm tra claim {@code iss} đúng là Auth của ta.</li>
     * </ol>
     *
     * <p>Vì danh tính đến từ token đã xác thực bằng mật mã (không tin header do client/
     * gateway gắn vào), service vẫn an toàn dù bị gọi thẳng trong cluster.
     *
     * <p>{@code @ConditionalOnMissingBean}: service nào muốn tự cấu hình decoder riêng
     * chỉ cần khai một bean {@code JwtDecoder} của mình — bean mặc định này sẽ tự nhường.
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            ResourceServerProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        // Ghép validator mặc định (hạn token) với validator issuer (đúng người phát).
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                JwtValidators.createDefault(),
                new JwtIssuerValidator(props.getIssuer())));
        return decoder;
    }

    /**
     * Chuyển claim {@code roles} trong token (vd {@code ["USER"]}) thành authority mà
     * Spring Security hiểu để phân quyền: {@code USER} → {@code ROLE_USER}.
     * (Bản servlet; bản Reactive tương đương nằm ở apigateway/SecurityConfig.)
     */
    private static JwtAuthenticationConverter rolesAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(ROLES_CLAIM);
        authorities.setAuthorityPrefix(ROLE_PREFIX);
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
