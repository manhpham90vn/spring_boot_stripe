package com.manhpham.payment.dto;

import com.manhpham.payment.entities.Payment;
import com.manhpham.payment.entities.PaymentStatus;

import java.util.UUID;

/**
 * Kết quả tạo PaymentIntent trả về Order: id bản ghi payment + tham chiếu PI + {@code clientSecret}
 * (để FE xác nhận bằng Payment Element) + trạng thái. {@code clientSecret} KHÔNG lưu DB.
 */
public record IntentResponse(UUID paymentId, String stripePiId, String clientSecret, PaymentStatus status) {

    public static IntentResponse of(Payment p, String clientSecret) {
        return new IntentResponse(p.getId(), p.getStripePiId(), clientSecret, p.getStatus());
    }
}
