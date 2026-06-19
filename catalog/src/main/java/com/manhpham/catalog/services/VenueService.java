package com.manhpham.catalog.services;

import com.manhpham.catalog.dto.CreateVenueRequest;
import com.manhpham.catalog.dto.UpdateVenueRequest;
import com.manhpham.catalog.dto.VenueResponse;

import java.util.List;
import java.util.UUID;

public interface VenueService {

    VenueResponse create(CreateVenueRequest request);

    List<VenueResponse> listAll();

    /** Dùng nội bộ khi ghép Venue vào Event. Ném {@code VenueNotFoundException} nếu thiếu. */
    VenueResponse get(UUID id);

    VenueResponse update(UUID id, UpdateVenueRequest request);

    /** Xóa địa điểm; ném {@code CatalogConflictException} nếu còn sự kiện tham chiếu. */
    void delete(UUID id);
}
