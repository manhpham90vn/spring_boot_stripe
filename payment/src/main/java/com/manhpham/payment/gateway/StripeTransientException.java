package com.manhpham.payment.gateway;

/**
 * Bọc lỗi Stripe ĐÁNG retry (429 rate-limit, timeout kết nối, 5xx) thành RuntimeException
 * để Resilience4j {@code @Retry} bắt và thử lại với backoff. Lỗi nghiệp vụ 4xx (thẻ bị từ
 * chối, request sai) KHÔNG bọc qua đây — không retry (payment_issue.md 2.18).
 */
public class StripeTransientException extends RuntimeException {
    public StripeTransientException(Throwable cause) {
        super(cause);
    }
}
