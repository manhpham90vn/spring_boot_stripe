package com.manhpham.apigateway.config;

import java.time.Duration;

import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import reactor.core.publisher.Mono;

/**
 * Cấu hình hai cơ chế phòng thủ ở biên cho flash sale:
 * <ul>
 *   <li><b>Rate limiter</b> (qua {@link #clientKeyResolver()}): giới hạn số request
 *       MỖI client. Bản thân giới hạn (replenishRate/burstCapacity) khai trong
 *       {@code application.properties} cho từng route; bean này chỉ quyết định
 *       "tính theo ai".</li>
 *   <li><b>Circuit breaker</b> ({@link #defaultCircuitBreaker()}): khi service hạ
 *       nguồn lỗi/chậm thì "ngắt mạch", trả fallback ngay thay vì để request dồn ứ —
 *       quan trọng vì on-prem KHÔNG autoscale, phải bảo vệ tài nguyên cố định.</li>
 * </ul>
 */
@Configuration
public class GatewayConfig {

    /**
     * Quyết định rate limiter đếm quota "theo ai". Ưu tiên danh tính trong JWT (sub =
     * id user) để mỗi user một quota công bằng; nếu request chưa có token (vd /api/auth)
     * thì rơi về địa chỉ IP. Trả về key này cho filter RequestRateLimiter trong route.
     */
    @Bean
    public KeyResolver clientKeyResolver() {
        return exchange -> exchange.getPrincipal()
                // Chỉ nhận principal là Authentication (đã verify JWT), bỏ qua loại khác.
                .filter(Authentication.class::isInstance)
                // Tên principal = subject của token = id user → quota theo user.
                .map(java.security.Principal::getName)
                // Chưa đăng nhập (vd đăng ký/đăng nhập) → đếm theo IP để vẫn chặn lạm dụng.
                .switchIfEmpty(Mono.fromSupplier(() -> clientIp(exchange)));
    }

    /**
     * Lấy IP thật của client. Sau Ingress/MetalLB (on-prem), IP gốc nằm ở header
     * {@code X-Forwarded-For} (phần tử đầu = client gốc), không phải remoteAddress
     * (vốn là IP của proxy). Không có header thì dùng remoteAddress, cuối cùng "unknown".
     */
    private static String clientIp(org.springframework.web.server.ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        var remote = exchange.getRequest().getRemoteAddress();
        return remote != null ? remote.getAddress().getHostAddress() : "unknown";
    }

    /**
     * Cấu hình MẶC ĐỊNH cho mọi circuit breaker (mỗi route tham chiếu 1 cái qua tên
     * {@code authCb}, {@code catalogCb}... trong properties). Ý nghĩa các tham số:
     * <ul>
     *   <li><b>timeout 3s</b>: gọi service hạ nguồn quá 3s coi như thất bại → không để
     *       request treo giữ thread/kết nối khi đang cao tải.</li>
     *   <li><b>slidingWindowSize 20</b>: thống kê trên 20 lần gọi gần nhất.</li>
     *   <li><b>failureRateThreshold 50%</b>: quá nửa số lần đó lỗi thì MỞ mạch (open),
     *       request kế tiếp đi thẳng tới fallback, không gọi service nữa.</li>
     *   <li><b>waitDurationInOpenState 10s</b>: giữ mạch mở 10s cho service kịp hồi, rồi
     *       half-open để thử lại dè dặt.</li>
     * </ul>
     */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCircuitBreaker() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(3))
                        .build())
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(20)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        .build())
                .build());
    }
}
