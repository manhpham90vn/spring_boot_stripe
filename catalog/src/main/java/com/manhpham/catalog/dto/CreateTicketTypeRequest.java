package com.manhpham.catalog.dto;

import com.manhpham.catalog.entities.TicketTypeKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Tạo loại vé. {@code kind=GA} (mặc định) → bắt buộc {@code totalQty}. {@code kind=SEATED} →
 * bắt buộc danh sách {@code seats} (mỗi ghế vào seat_map; tồn = số ghế). Cross-field validate
 * ở service. Tồn kho KHÔNG lưu ở Catalog — chuyển thẳng sang Inventory để seed.
 */
public record CreateTicketTypeRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        // null → mặc định GA (giữ tương thích payload cũ).
        TicketTypeKind kind,
        // Đơn vị nhỏ nhất (minor units). Với JPY = chính số tiền (zero-decimal).
        @NotNull @PositiveOrZero Long priceMinor,
        // ISO-4217, vd "USD", "JPY", "VND".
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "currency phải là mã ISO-4217 3 ký tự in hoa") String currency,
        @Min(1) int maxPerOrder,
        // GA: tổng số vé (bắt buộc). SEATED: bỏ trống (tồn = số ghế).
        @PositiveOrZero Integer totalQty,
        // SEATED: danh sách ghế (bắt buộc, không rỗng). GA: bỏ trống.
        @Valid List<SeatInput> seats) {

    /** GA nếu không khai kind. */
    public TicketTypeKind kindOrDefault() {
        return kind == null ? TicketTypeKind.GA : kind;
    }
}
