package com.manhpham.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Tinh chỉnh resource-server dùng chung cho từng service. JWKS URI vẫn dùng
 * property chuẩn của Spring: {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}.
 */
@ConfigurationProperties(prefix = "app.security")
public class ResourceServerProperties {

    /** Giá trị claim {@code iss} bắt buộc; phải khớp auth.jwt.issuer của Auth. */
    private String issuer = "auth";

    /**
     * Các path public riêng của service (ngoài actuator/swagger đã mở sẵn).
     * Ví dụ Catalog mở {@code /api/catalog/public/**}, Payment mở {@code /webhooks/**}.
     */
    private List<String> publicPaths = new ArrayList<>();

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }
}
