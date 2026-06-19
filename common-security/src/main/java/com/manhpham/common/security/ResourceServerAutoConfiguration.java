package com.manhpham.common.security;

import java.util.Arrays;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
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
 * <p>Service chỉ cần: thêm dependency {@code common-security} và đặt
 * {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri} +
 * {@code app.security.issuer}. Muốn ghi đè thì tự khai {@code SecurityFilterChain}
 * hoặc {@code JwtDecoder} của riêng mình — các bean dưới đây sẽ tự nhường.
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

    @Bean
    @ConditionalOnMissingBean
    public SecurityFilterChain resourceServerSecurityFilterChain(HttpSecurity http,
            ResourceServerProperties props) throws Exception {
        String[] publicPaths = Stream.concat(
                        Arrays.stream(INFRA_PUBLIC),
                        props.getPublicPaths().stream())
                .toArray(String[]::new);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicPaths).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(rolesAuthenticationConverter())));
        return http.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            ResourceServerProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                JwtValidators.createDefault(),
                new JwtIssuerValidator(props.getIssuer())));
        return decoder;
    }

    /** Map claim {@code roles} (vd {@code ["USER"]}) sang authority {@code ROLE_USER}. */
    private static JwtAuthenticationConverter rolesAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
