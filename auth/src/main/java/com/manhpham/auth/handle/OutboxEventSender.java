package com.manhpham.auth.handle;

import com.manhpham.auth.core.dto.OutboxEvent;
import com.manhpham.auth.entities.OutboxEventEntity;
import com.manhpham.auth.repositories.jpa.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * The single bridge between the typed domain event and its durable form. Business
 * services call {@link #fire(OutboxEvent)} from inside their own transaction; they
 * stay unaware of JSON serialization, the table or the transport. Swapping the
 * publisher (poller ↔ Debezium) never touches a caller of this class.
 */
@Component
@RequiredArgsConstructor
public class OutboxEventSender {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Persists the event into the outbox. Must run within the caller's business
     * transaction so the event and the business change commit atomically.
     */
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
