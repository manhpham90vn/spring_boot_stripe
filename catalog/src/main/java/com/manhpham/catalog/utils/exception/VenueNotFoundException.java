package com.manhpham.catalog.utils.exception;

import java.util.UUID;

public class VenueNotFoundException extends RuntimeException {
    public VenueNotFoundException(UUID id) {
        super("Venue not found: " + id);
    }
}
