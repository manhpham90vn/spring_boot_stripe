package com.manhpham.catalog.repositories.jpa;

import com.manhpham.catalog.entities.Event;
import com.manhpham.catalog.entities.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    /** Danh sách công khai: bỏ các sự kiện chưa phát hành (DRAFT). */
    List<Event> findByStatusNotOrderByStartsAtAsc(EventStatus status);

    /** Danh sách admin: tất cả (gồm DRAFT), sắp theo giờ diễn. */
    List<Event> findAllByOrderByStartsAtAsc();

    /** Guard xóa địa điểm: còn sự kiện tham chiếu thì không cho xóa. */
    boolean existsByVenueId(UUID venueId);
}
