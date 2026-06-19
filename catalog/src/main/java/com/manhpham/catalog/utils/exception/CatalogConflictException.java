package com.manhpham.catalog.utils.exception;

/**
 * Thao tác ghi vi phạm ràng buộc trạng thái/nghiệp vụ của Catalog (map sang HTTP
 * 409). Ví dụ: sửa/xóa sự kiện đã phát hành, xóa địa điểm còn sự kiện tham chiếu.
 */
public class CatalogConflictException extends RuntimeException {
    public CatalogConflictException(String message) {
        super(message);
    }
}
