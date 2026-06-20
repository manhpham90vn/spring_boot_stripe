package com.manhpham.catalog.repositories.jpa;

import com.manhpham.catalog.entities.Event;
import com.manhpham.catalog.entities.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository cho Event. Kế thừa {@link JpaRepository} là có sẵn CRUD (save/findById/
 * findAll/delete...). Các phương thức dưới đây là "derived query": Spring Data tự SINH
 * câu SQL DỰA TRÊN TÊN HÀM — không cần viết SQL. Vd {@code findByStatusNotOrderByStartsAtAsc}
 * được hiểu là "WHERE status <> ? ORDER BY starts_at ASC". Đặt tên đúng quy ước là đủ.
 */
public interface EventRepository extends JpaRepository<Event, UUID> {

    /** Danh sách công khai: bỏ các sự kiện chưa phát hành (DRAFT). */
    List<Event> findByStatusNotOrderByStartsAtAsc(EventStatus status);

    /** Danh sách admin: tất cả (gồm DRAFT), sắp theo giờ diễn. */
    List<Event> findAllByOrderByStartsAtAsc();

    /** Guard xóa địa điểm: còn sự kiện tham chiếu thì không cho xóa. */
    boolean existsByVenueId(UUID venueId);
}
