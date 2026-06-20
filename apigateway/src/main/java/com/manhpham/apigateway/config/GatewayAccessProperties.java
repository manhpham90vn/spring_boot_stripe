package com.manhpham.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Edge-policy của gateway, KHAI BÁO bằng properties ({@code app.gateway.access.rules[...]}).
 * Đây là NƠI DUY NHẤT quyết định ở BIÊN: route nào công khai, route nào bắt buộc có token.
 *
 * <p>Cố ý chỉ phân biệt PUBLIC vs cần-token — KHÔNG xét role ở gateway. Luật theo role
 * (vd ADMIN) thuộc về từng service (common-security), để gateway không phải biết chi tiết
 * nghiệp vụ của service và không bị trùng lặp luật. Xem docs/SECURITY-ACCESS-CONTROL.md.
 */
@ConfigurationProperties(prefix = "app.gateway.access")
public class GatewayAccessProperties {

    /** Luật xét theo thứ tự khai báo (khớp trước thắng). Không khớp → authenticated. */
    private List<Rule> rules = new ArrayList<>();

    public List<Rule> getRules() {
        return rules;
    }

    public void setRules(List<Rule> rules) {
        this.rules = rules;
    }

    /** Một luật ở biên: "request khớp {method + pattern} thì public hay cần token". */
    public static class Rule {

        /** Ant pattern bắt buộc, vd {@code /api/catalog/**}. */
        private String pattern;

        /** HTTP method (GET/POST/...). Bỏ trống = khớp MỌI method. */
        private String method;

        /** PERMIT_ALL (công khai) hoặc AUTHENTICATED (cần JWT hợp lệ). Mặc định AUTHENTICATED. */
        private Access access = Access.AUTHENTICATED;

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
    }

    /** Mức truy cập ở biên (gateway không xét role). */
    public enum Access {
        PERMIT_ALL,
        AUTHENTICATED
    }
}
