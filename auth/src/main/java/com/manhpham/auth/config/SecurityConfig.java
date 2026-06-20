package com.manhpham.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Bảo mật của Auth service. Auth vừa là nơi PHÁT token, vừa tự verify token của chính
 * mình cho các endpoint cần đăng nhập (vd /api/auth/me). Điểm khác các service khác:
 * Auth verify bằng public key CỤC BỘ (đã có sẵn cặp khoá trong process), không cần
 * gọi JWKS qua mạng như common-security/gateway.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // API stateless: không session, không CSRF.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                // Nhánh công khai theo quy ước path: register, login (chưa
                                // đăng nhập thì lấy đâu ra token). /api/auth/me KHÔNG nằm ở
                                // đây nên rơi vào anyRequest = cần JWT.
                                "/api/auth/public/**",
                                // API nội bộ service↔service (quy ước /internal/**): permitAll
                                // ở app, rào thật ở tầng mạng (gateway không route + NetworkPolicy).
                                // Auth tự khai vì KHÔNG dùng common-security; giữ chuẩn đồng nhất.
                                // GỒM LUÔN JWKS (/internal/jwks) — chỉ service↔service tải public key.
                                "/internal/**",
                                // Tài liệu API + actuator hạ tầng (nhóm "platform", path do framework cố định).
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/prometheus",
                                "/actuator/metrics/**")
                        .permitAll()
                        // Mọi endpoint khác (vd /api/auth/me) cần Bearer JWT hợp lệ.
                        .anyRequest().authenticated())
                // Customizer.withDefaults() = dùng bean JwtDecoder bên dưới để verify Bearer token.
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * Decoder verify token do CHÍNH Auth phát. Khác gateway/common-security (tải public
     * key qua JWKS rồi cache): ở đây Auth đã giữ sẵn cặp khoá trong process nên dùng
     * thẳng public key cục bộ ({@code withPublicKey}) — nhanh hơn, không phụ thuộc mạng.
     * Vẫn ghép validator mặc định (kiểm hạn {@code exp}) với validator issuer ({@code iss}).
     */
    @Bean
    JwtDecoder jwtDecoder(JwtKeys keys, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.getPublicKey()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                JwtValidators.createDefault(),
                new JwtIssuerValidator(properties.getIssuer())));
        return decoder;
    }

    /**
     * BCrypt để hash mật khẩu: hàm băm một chiều có salt + cố tình chậm (chống brute-force).
     * Dùng cho cả lúc đăng ký (encode) lẫn đăng nhập (matches) trong AuthServiceImpl.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
