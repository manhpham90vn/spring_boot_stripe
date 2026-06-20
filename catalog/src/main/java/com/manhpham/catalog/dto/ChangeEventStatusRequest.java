package com.manhpham.catalog.dto;

import com.manhpham.catalog.entities.EventStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Body cho endpoint đổi trạng thái sự kiện. Jackson tự ánh xạ chuỗi JSON (vd "ON_SALE")
 * sang enum {@link EventStatus}; gửi giá trị không thuộc enum sẽ bị từ chối ngay khi parse.
 */
public record ChangeEventStatusRequest(@NotNull EventStatus status) {
}
