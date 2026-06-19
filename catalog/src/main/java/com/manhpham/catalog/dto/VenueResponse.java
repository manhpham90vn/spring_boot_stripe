package com.manhpham.catalog.dto;

import com.manhpham.catalog.entities.Venue;

import java.io.Serializable;
import java.util.UUID;

public record VenueResponse(
        UUID id,
        String name,
        String address,
        String city) implements Serializable {

    public static VenueResponse from(Venue v) {
        return new VenueResponse(v.getId(), v.getName(), v.getAddress(), v.getCity());
    }
}
