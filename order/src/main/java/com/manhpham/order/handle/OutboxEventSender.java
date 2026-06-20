package com.manhpham.order.handle;

import com.manhpham.order.core.dto.OutboxEvent;
import com.manhpham.order.entities.OutboxEventEntity;
import com.manhpham.order.repositories.jpa.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Cây cầu duy nhất giữa domain event và bảng outbox. Service gọi {@link #fire(OutboxEvent)}
 * TỪ TRONG transaction của mình → event và thay đổi nghiệp vụ commit nguyên tử.
 */
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
