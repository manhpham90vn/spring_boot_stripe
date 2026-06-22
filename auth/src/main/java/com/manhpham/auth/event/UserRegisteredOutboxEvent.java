package com.manhpham.auth.event;

import com.manhpham.common.core.dto.OutboxEvent;
import com.manhpham.common.core.event.UserRegisteredEvent;

/**
 * Bọc {@link UserRegisteredEvent} thành một bản ghi outbox cụ thể: gắn payload nghiệp vụ
 * với 3 metadata định tuyến (aggregateType/aggregateId/eventType). Tách "payload" khỏi
 * "metadata outbox" giúp event nghiệp vụ không dính chi tiết hạ tầng messaging.
 *
 * <p>{@code final}: không cho kế thừa tiếp — mỗi loại event là một lớp lá rõ ràng.
 */
public final class UserRegisteredOutboxEvent extends OutboxEvent<UserRegisteredEvent> {

    private UserRegisteredOutboxEvent(UserRegisteredEvent payload) {
        super(payload);
    }

    public static UserRegisteredOutboxEvent of(UserRegisteredEvent payload) {
        return new UserRegisteredOutboxEvent(payload);
    }

    @Override
    public String getAggregateType() {
        // "user" → Debezium dùng làm tên topic (vd outbox.event.user).
        return "user";
    }

    @Override
    public String getAggregateId() {
        // id user làm key → mọi event của cùng một user vào cùng partition, giữ đúng thứ tự.
        return getPayload().userId().toString();
    }

    @Override
    public String getEventType() {
        // Consumer đọc trường này để biết "đây là biến cố đăng ký" mà xử lý đúng nhánh.
        return "UserRegistered";
    }
}
