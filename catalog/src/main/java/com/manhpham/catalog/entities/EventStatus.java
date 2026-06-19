package com.manhpham.catalog.entities;

/**
 * Vòng đời hiển thị/bán của một sự kiện trong Catalog.
 *
 * <p>Lưu ý: Catalog KHÔNG biết tồn kho, nên không có trạng thái {@code SOLD_OUT}
 * ở đây — "hết vé" do Inventory quyết định dựa trên số lượng thực còn lại.
 */
public enum EventStatus {
    /** Mới tạo, chưa công khai — chỉ admin thấy. */
    DRAFT,
    /** Đang mở bán, hiển thị công khai. */
    ON_SALE,
    /** Đã đóng bán (qua giờ hoặc admin đóng), còn hiển thị để tra cứu. */
    CLOSED,
    /** Bị huỷ. */
    CANCELLED
}
