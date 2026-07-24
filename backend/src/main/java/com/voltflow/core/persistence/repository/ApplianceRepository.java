package com.voltflow.core.persistence.repository;

import com.voltflow.core.persistence.entity.ApplianceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplianceRepository extends JpaRepository<ApplianceEntity, Long> {
    List<ApplianceEntity> findByHomeIdOrderById(Long homeId);
}
