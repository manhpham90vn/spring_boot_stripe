package com.manhpham.waitingroom.dto;

/** Kết quả verify PASS cho hạ nguồn (Order/Gateway gọi {@code /internal/admission/check}). */
public record AdmissionCheckResponse(boolean valid) {
}
