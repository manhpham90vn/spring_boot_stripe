package com.manhpham.payment.handle;

import com.manhpham.common.core.dto.OutboxEvent;
import com.manhpham.payment.entities.OutboxEventEntity;
import com.manhpham.payment.repositories.jpa.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Ghi domain event vào bảng outbox (cùng transaction với thay đổi nghiệp vụ). */
@Component
@RequiredArgsConstructor
public class OutboxEventSender {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public void fire(OutboxEvent<?> event) {
        repository.save(OutboxEventEntity.create(
                event.getAggregateType(),
                event.getAggregateId(),
                event.getEventType(),
                toJson(event.getPayload())));
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize outbox payload " + payload.getClass(), e);
        }
    }
}
