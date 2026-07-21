package com.voltwise.core.persistence.repository;

import com.voltwise.core.persistence.entity.ApplianceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplianceRepository extends JpaRepository<ApplianceEntity, Long> {
    List<ApplianceEntity> findByHomeIdOrderById(Long homeId);
}
