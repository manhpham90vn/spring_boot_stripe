package com.manhpham.catalog.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Khai báo security scheme Bearer JWT cho Swagger UI → nút "Authorize" để dán
 * access token (lấy từ {@code POST /api/auth/public/login} của Auth). KHÔNG đặt
 * {@code security} ở mức global vì phần lớn endpoint Catalog là đọc công khai;
 * chỉ endpoint admin mới cần token.
 */
@Configuration
@OpenAPIDefinition(info = @Info(title = "Catalog Service API", version = "v1"))
@SecurityScheme(
        name = OpenApiConfig.BEARER_SCHEME,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";
}
