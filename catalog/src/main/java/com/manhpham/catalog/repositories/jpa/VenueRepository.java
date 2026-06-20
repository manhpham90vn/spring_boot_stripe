package com.manhpham.catalog.repositories.jpa;

import com.manhpham.catalog.entities.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository cho Venue. Chỉ cần CRUD cơ bản nên để trống — {@link JpaRepository} đã cấp
 * sẵn save/findById/findAll/findAllById/existsById/deleteById... mà không phải khai gì thêm.
 */
public interface VenueRepository extends JpaRepository<Venue, UUID> {
}
