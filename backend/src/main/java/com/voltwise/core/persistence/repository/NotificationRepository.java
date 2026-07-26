package com.voltwise.core.persistence.repository;

import com.voltwise.core.persistence.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    List<NotificationEntity> findAllByOrderByCreatedAtDesc();
    List<NotificationEntity> findByRecipientIgnoreCaseOrderByIdDesc(String recipient);
}
