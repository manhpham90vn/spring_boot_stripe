package com.manhpham.payment.controller;

import com.manhpham.payment.dto.ChargeRequest;
import com.manhpham.payment.dto.ChargeResponse;
import com.manhpham.payment.services.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * API NỘI BỘ của Payment ({@code /internal/**}): chỉ Order/saga gọi. Webhook Stripe đi qua
 * đường KHÁC ({@code /webhooks/stripe}, vào thẳng qua nginx/Ingress DMZ — chưa làm ở walking
 * skeleton, xem payment-stripe-flow.md). Không JWT ở đây (rào bằng mạng).
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalPaymentController {

    private final PaymentService payments;

    /** Thu tiền cho một đơn (saga bước 3). */
    @PostMapping("/charges")
    public ChargeResponse charge(@Valid @RequestBody ChargeRequest request) {
        return payments.charge(request);
    }

    /** Tra trạng thái thanh toán của một đơn — Order dùng cho reconciliation (khi mất event). */
    @GetMapping("/payments/by-order/{orderId}")
    public ChargeResponse byOrder(@PathVariable UUID orderId) {
        return payments.getByOrder(orderId);
    }
}
