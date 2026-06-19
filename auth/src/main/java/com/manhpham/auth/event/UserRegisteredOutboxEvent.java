package com.manhpham.auth.event;

import com.manhpham.auth.core.dto.OutboxEvent;

public final class UserRegisteredOutboxEvent extends OutboxEvent<UserRegisteredEvent> {

    private UserRegisteredOutboxEvent(UserRegisteredEvent payload) {
        super(payload);
    }

    public static UserRegisteredOutboxEvent of(UserRegisteredEvent payload) {
        return new UserRegisteredOutboxEvent(payload);
    }

    @Override
    public String getAggregateType() {
        return "user";
    }

    @Override
    public String getAggregateId() {
        return getPayload().userId().toString();
    }

    @Override
    public String getEventType() {
        return "UserRegistered";
    }
}
