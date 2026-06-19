package com.manhpham.apigateway.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

/**
 * Đích đến khi circuit breaker MỞ mạch. Các route khai
 * {@code fallbackUri=forward:/__fallback}, nên khi service hạ nguồn lỗi/timeout,
 * gateway forward NỘI BỘ sang đây thay vì để client treo hoặc nhận lỗi 500 thô.
 * Path {@code /__fallback} được permitAll trong SecurityConfig (không cần JWT).
 */
@RestController
public class FallbackController {

    @RequestMapping("/__fallback")
    public Mono<ResponseEntity<Map<String, Object>>> fallback() {
        // 503 + Retry ngụ ý: lỗi tạm thời, client cứ thử lại sau (đúng bản chất mạch mở).
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "status", 503,
                        "error", "Service Unavailable",
                        "message", "Upstream service is temporarily unavailable, please retry shortly.")));
    }
}
