package com.manhpham.notification.repositories.jpa;

import com.manhpham.notification.entities.SentNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SentNotificationRepository extends JpaRepository<SentNotification, UUID> {

    Optional<SentNotification> findByDedupKey(String dedupKey);
}
