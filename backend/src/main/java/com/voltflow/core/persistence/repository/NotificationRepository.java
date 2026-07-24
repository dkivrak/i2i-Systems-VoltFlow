package com.voltflow.core.persistence.repository;

import com.voltflow.core.persistence.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {}
