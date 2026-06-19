package com.manhpham.catalog.services.impl;

import com.manhpham.catalog.dto.CreateVenueRequest;
import com.manhpham.catalog.dto.UpdateVenueRequest;
import com.manhpham.catalog.dto.VenueResponse;
import com.manhpham.catalog.entities.Venue;
import com.manhpham.catalog.repositories.jpa.EventRepository;
import com.manhpham.catalog.repositories.jpa.VenueRepository;
import com.manhpham.catalog.services.VenueService;
import com.manhpham.catalog.utils.exception.CatalogConflictException;
import com.manhpham.catalog.utils.exception.VenueNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VenueServiceImpl implements VenueService {

    private final VenueRepository venues;
    private final EventRepository events;

    @Override
    @Transactional
    @CacheEvict(cacheNames = "venues", allEntries = true)
    public VenueResponse create(CreateVenueRequest request) {
        Venue saved = venues.save(Venue.create(request.name(), request.address(), request.city()));
        return VenueResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "venues")
    public List<VenueResponse> listAll() {
        return venues.findAll().stream().map(VenueResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public VenueResponse get(UUID id) {
        return venues.findById(id).map(VenueResponse::from)
                .orElseThrow(() -> new VenueNotFoundException(id));
    }

    @Override
    @Transactional
    // Đổi địa điểm → event summary/detail (nhúng venue) có thể stale → evict luôn.
    @Caching(evict = {
            @CacheEvict(cacheNames = "venues", allEntries = true),
            @CacheEvict(cacheNames = "events", allEntries = true),
            @CacheEvict(cacheNames = "event", allEntries = true)
    })
    public VenueResponse update(UUID id, UpdateVenueRequest request) {
        Venue venue = venues.findById(id).orElseThrow(() -> new VenueNotFoundException(id));
        venue.update(request.name(), request.address(), request.city());
        return VenueResponse.from(venue);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "venues", allEntries = true)
    public void delete(UUID id) {
        if (!venues.existsById(id)) {
            throw new VenueNotFoundException(id);
        }
        if (events.existsByVenueId(id)) {
            throw new CatalogConflictException(
                    "Không thể xóa địa điểm còn sự kiện tham chiếu: " + id);
        }
        venues.deleteById(id);
    }
}
