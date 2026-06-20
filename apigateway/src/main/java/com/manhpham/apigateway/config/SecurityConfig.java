package com.manhpham.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.AuthorizeExchangeSpec;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Bảo mật tại API Gateway (WebFlux/Netty thuần — KHÔNG có DB, KHÔNG business logic).
 *
 * <p>Gateway là cửa vào duy nhất cho traffic nghiệp vụ. Mỗi request phải mang
 * Bearer JWT do Auth service phát. Gateway VERIFY token bằng public key (lấy từ
 * JWKS của Auth, cache lại) — KHÔNG gọi Auth mỗi request, nên không chẹn event-loop
 * lúc flash sale. Sau khi verify, request mới được route xuống service hạ nguồn.
 *
 * <p>Lưu ý: các service hạ nguồn (Catalog, Order...) cũng tự verify lại JWT bằng
 * {@code common-security}. Gateway verify để chặn sớm ở biên; service verify lại
 * để không tin tưởng mù quáng dù bị gọi thẳng trong cluster (defense in depth).
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(GatewayAccessProperties.class)
public class SecurityConfig {

    /** Path hạ tầng luôn mở ở biên: K8s health probe, scrape Prometheus, trang fallback. */
    private static final String[] INFRA_PUBLIC = {
            "/actuator/health/**", "/actuator/info", "/actuator/prometheus", "/__fallback"
    };

    /**
     * Tên claim chứa danh sách role trong JWT, và prefix mà Spring Security quy ước
     * cho authority. Quy ước này phải KHỚP với phía phát token (Auth) và với
     * {@code common-security} ở các service hạ nguồn — sửa ở đây thì nhớ sửa cả ở đó.
     */
    private static final String ROLES_CLAIM = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
                                                            ReactiveJwtDecoder jwtDecoder,
                                                            GatewayAccessProperties access) {
        http
            // Gateway là API stateless (không session, không form login) → tắt CSRF.
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchange -> applyAccessRules(exchange, access))
            // Bật resource-server: lấy Bearer token, verify bằng jwtDecoder bên dưới,
            // rồi chuyển claim "roles" thành authority để phân quyền.
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                .jwtDecoder(jwtDecoder)
                .jwtAuthenticationConverter(rolesAuthenticationConverter())));
        return http.build();
    }

    /**
     * Dựng edge-policy từ KHAI BÁO trong properties (xem {@link GatewayAccessProperties}).
     * Thứ tự đăng ký = thứ tự xét (khớp trước thắng): hạ tầng trước, rồi {@code rules} theo
     * đúng thứ tự khai báo, cuối cùng anyExchange = authenticated.
     */
    private static void applyAccessRules(AuthorizeExchangeSpec exchange, GatewayAccessProperties access) {
        // Path hạ tầng (health/metrics) + fallback của circuit breaker → luôn mở.
        exchange.pathMatchers(INFRA_PUBLIC).permitAll();
        for (GatewayAccessProperties.Rule rule : access.getRules()) {
            var matcher = (rule.getMethod() == null || rule.getMethod().isBlank())
                    ? exchange.pathMatchers(rule.getPattern())
                    : exchange.pathMatchers(HttpMethod.valueOf(rule.getMethod().toUpperCase()), rule.getPattern());
            switch (rule.getAccess()) {
                case PERMIT_ALL -> matcher.permitAll();
                case AUTHENTICATED -> matcher.authenticated();
            }
        }
        // Mọi request còn lại bắt buộc có JWT hợp lệ.
        exchange.anyExchange().authenticated();
    }

    /**
     * Decoder verify JWT CỤC BỘ tại gateway. Một dòng {@code withJwkSetUri(...)} của
     * Spring Security trông như "magic" nhưng thực chất nó tự động làm các bước sau:
     *
     * <ol>
     *   <li>Đọc header của JWT, lấy {@code kid} (id của key đã ký).</li>
     *   <li>Tải JWKS (tập public key) từ {@code jwkSetUri} của Auth và CACHE lại
     *       → không gọi Auth cho từng request, tránh chẹn event-loop khi flash sale.</li>
     *   <li>Chọn đúng public key theo {@code kid} rồi verify CHỮ KÝ RS256 của token.</li>
     *   <li>{@code JwtValidators.createDefault()}: kiểm tra token còn hạn ({@code exp})
     *       và đã tới thời điểm hiệu lực ({@code nbf}/{@code iat}).</li>
     *   <li>{@code JwtIssuerValidator}: kiểm tra claim {@code iss} đúng là Auth của ta
     *       → chặn token do hệ khác phát dù chữ ký hợp lệ.</li>
     * </ol>
     *
     * <p>Phần mật mã (verify chữ ký, quản cache JWKS) cố tình để framework lo — đây là
     * mảnh dễ sai bảo mật nhất, không nên tự viết tay.
     *
     * <p>Property {@code app.security.issuer} dùng CHUNG một tên với các service hạ
     * nguồn (xem {@code common-security}) để tránh nhầm lẫn "hai khái niệm khác nhau".
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${app.security.issuer:auth}") String issuer) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        // Ghép validator mặc định (hạn token) với validator issuer (đúng người phát).
        OAuth2TokenValidator<Jwt> withIssuer = new JwtIssuerValidator(issuer);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), withIssuer));
        return decoder;
    }

    /**
     * Chuyển claim {@code roles} trong token (ví dụ {@code ["USER"]}) thành authority
     * mà Spring Security hiểu để phân quyền: {@code USER} → {@code ROLE_USER}.
     * (Bản Reactive vì gateway chạy WebFlux; bản servlet tương đương nằm ở common-security.)
     */
    private ReactiveJwtAuthenticationConverterAdapter rolesAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(ROLES_CLAIM);
        authorities.setAuthorityPrefix(ROLE_PREFIX);
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
