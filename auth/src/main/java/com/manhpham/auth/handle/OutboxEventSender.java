package com.manhpham.auth.handle;

import com.manhpham.auth.core.dto.OutboxEvent;
import com.manhpham.auth.entities.OutboxEventEntity;
import com.manhpham.auth.repositories.jpa.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Cây cầu DUY NHẤT giữa domain event (có kiểu) và dạng lưu bền của nó. Service nghiệp vụ
 * gọi {@link #fire(OutboxEvent)} TỪ BÊN TRONG transaction của mình; chúng không cần biết
 * gì về serialize JSON, tên bảng hay cơ chế vận chuyển. Nhờ vậy, sau này muốn đổi cách
 * phát event (vd từ poller sang Debezium) cũng không phải sửa bất kỳ caller nào.
 */
@Component
@RequiredArgsConstructor
public class OutboxEventSender {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Ghi event xuống bảng outbox. PHẢI chạy trong transaction nghiệp vụ của caller để
     * event và thay đổi nghiệp vụ commit nguyên tử (atomic) — cùng thành công hoặc cùng hủy.
     */
    public void fire(OutboxEvent<?> event) {
        // CHỈ ghi event xuống bảng outbox (cùng transaction với thay đổi nghiệp vụ) —
        // KHÔNG tự đẩy lên Kafka ở đây. Debezium đọc WAL của Postgres rồi mới publish.
        // Nhờ commit chung 1 transaction: thay đổi nghiệp vụ và event không bao giờ lệch nhau.
        repository.save(OutboxEventEntity.create(
                event.getAggregateType(),
                event.getAggregateId(),
                event.getEventType(),
                toJson(event.getPayload())));
    }

    /** Serialize payload nghiệp vụ thành chuỗi JSON để lưu vào cột payload của bảng outbox. */
    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            // Lỗi serialize là lỗi lập trình (payload không hợp lệ) → ném runtime để
            // transaction rollback, không "nuốt" lỗi rồi ghi event rỗng.
            throw new IllegalStateException("Failed to serialize outbox payload " + payload.getClass(), e);
        }
    }
}
