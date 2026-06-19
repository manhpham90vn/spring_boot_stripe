package com.manhpham.catalog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Catalog đọc nhiều ghi ít: GET công khai (duyệt sự kiện/loại vé không cần đăng
 * nhập), còn ghi (admin) cần JWT có role ADMIN. Bean này GHI ĐÈ
 * {@code SecurityFilterChain} mặc định của common-security (vốn bắt mọi request
 * phải auth) — {@code JwtDecoder} từ common-security vẫn được dùng để verify chữ
 * ký/issuer/hạn cục bộ bằng JWKS của Auth.
 */
@Configuration
public class SecurityConfig {

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
    public SecurityFilterChain catalogSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(INFRA_PUBLIC).permitAll()
                        // Quản trị danh mục: cần JWT role ADMIN (mọi method).
                        .requestMatchers("/api/catalog/admin/**").hasRole("ADMIN")
                        // Duyệt danh mục: GET công khai.
                        .requestMatchers(HttpMethod.GET, "/api/catalog/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(rolesAuthenticationConverter())));
        return http.build();
    }

    /** Map claim {@code roles} (vd {@code ["ADMIN"]}) sang authority {@code ROLE_ADMIN}. */
    private static JwtAuthenticationConverter rolesAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
