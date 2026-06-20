package com.manhpham.order.controller;

import com.manhpham.order.dto.OrderResponse;
import com.manhpham.order.dto.PlaceOrderRequest;
import com.manhpham.order.services.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * API mua vé cho client. {@code /api/order/**} — cần JWT (common-security verify; gateway đã
 * verify ở biên). Danh tính người mua lấy từ claim {@code sub} của token đã xác thực.
 */
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orders;

    /** Đặt mua (chạy saga). 201 + đơn ở trạng thái cuối (PAID/REJECTED/PAYMENT_FAILED). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PlaceOrderRequest request) {
        return orders.place(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("email"), request);
    }

    /** Xem đơn của chính mình (IDOR: chỉ chủ đơn, khác → 404). */
    @GetMapping("/{id}")
    public OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return orders.get(UUID.fromString(jwt.getSubject()), id);
    }
}
