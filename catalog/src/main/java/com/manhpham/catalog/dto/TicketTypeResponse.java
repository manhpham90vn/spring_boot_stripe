package com.manhpham.catalog.dto;

import com.manhpham.catalog.entities.TicketType;
import com.manhpham.catalog.entities.TicketTypeKind;

import java.io.Serializable;
import java.util.UUID;

public record TicketTypeResponse(
        UUID id,
        String name,
        String description,
        TicketTypeKind kind,
        long priceMinor,
        String currency,
        int maxPerOrder) implements Serializable {

    public static TicketTypeResponse from(TicketType t) {
        return new TicketTypeResponse(t.getId(), t.getName(), t.getDescription(),
                t.getKind(), t.getPriceMinor(), t.getCurrency(), t.getMaxPerOrder());
    }
}
