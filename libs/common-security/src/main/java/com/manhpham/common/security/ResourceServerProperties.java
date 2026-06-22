package com.manhpham.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Tinh chỉnh resource-server dùng chung cho từng service. JWKS URI vẫn dùng
 * property chuẩn của Spring: {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}.
 *
 * <p>Đây là NƠI DUY NHẤT mỗi service khai luật phân quyền của mình (dưới dạng dữ liệu,
 * không phải code) — common-security đọc và dựng filter chain. Nhờ vậy không còn mỗi
 * service tự viết một SecurityConfig riêng rồi lệch nhau.
 */
@ConfigurationProperties(prefix = "app.security")
public class ResourceServerProperties {

    /** Giá trị claim {@code iss} bắt buộc; phải khớp auth.jwt.issuer của Auth. */
    private String issuer = "auth";

    /**
     * Lối tắt cho các path CÔNG KHAI đơn giản (permitAll, mọi HTTP method). Tương đương
     * một {@link Rule} có access = PERMIT_ALL. Giữ lại cho gọn; luật phức tạp dùng {@link #rules}.
     */
    private List<String> publicPaths = new ArrayList<>();

    /**
     * Danh sách luật truy cập, ÁP DỤNG THEO THỨ TỰ khai báo (khớp trước thắng). Vd Catalog:
     * <pre>
     * app.security.rules[0].pattern=/api/catalog/admin/**   # ghi: cần role ADMIN
     * app.security.rules[0].role=ADMIN
     * app.security.rules[1].method=GET                       # đọc: công khai
     * app.security.rules[1].pattern=/api/catalog/**
     * app.security.rules[1].access=PERMIT_ALL
     * </pre>
     * Mọi request không khớp luật nào → mặc định bắt buộc đăng nhập (authenticated).
     */
    private List<Rule> rules = new ArrayList<>();

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

    public List<Rule> getRules() {
        return rules;
    }

    public void setRules(List<Rule> rules) {
        this.rules = rules;
    }

    /** Mức truy cập của một luật. */
    public enum Access {
        /** Cho qua không cần token. */
        PERMIT_ALL,
        /** Cần JWT hợp lệ (bất kỳ role nào). */
        AUTHENTICATED,
        /** Cần role cụ thể (xem {@link Rule#getRole()}). */
        HAS_ROLE
    }

    /** Một luật: "request khớp {method + pattern} thì áp mức truy cập này". */
    public static class Rule {

        /** Ant pattern bắt buộc, vd {@code /api/catalog/admin/**}. */
        private String pattern;

        /** HTTP method (GET/POST/...). Bỏ trống = khớp MỌI method. */
        private String method;

        /** Mức truy cập khi không đặt {@link #role}. Mặc định AUTHENTICATED. */
        private Access access = Access.AUTHENTICATED;

        /** Đặt role (vd ADMIN) là ngầm hiểu HAS_ROLE, bất kể {@link #access}. */
        private String role;

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public Access getAccess() {
            return access;
        }

        public void setAccess(Access access) {
            this.access = access;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        /** Mức truy cập thực sự: có {@code role} thì luôn là HAS_ROLE, ngược lại lấy {@code access}. */
        public Access effectiveAccess() {
            return (role != null && !role.isBlank()) ? Access.HAS_ROLE : access;
        }
    }
}
