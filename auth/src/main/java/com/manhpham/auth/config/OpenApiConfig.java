package com.manhpham.auth.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Khai báo security scheme Bearer JWT cho Swagger UI → hiện nút "Authorize" để
 * dán access token. {@code @SecurityRequirement} ở mức global khiến mọi endpoint
 * (vd {@code /api/auth/me}) tự đính header {@code Authorization: Bearer <token>}
 * khi gọi thử. Token lấy từ {@code POST /api/auth/public/login}.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Auth Service API", version = "v1"),
        security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
@SecurityScheme(
        name = OpenApiConfig.BEARER_SCHEME,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";
}
