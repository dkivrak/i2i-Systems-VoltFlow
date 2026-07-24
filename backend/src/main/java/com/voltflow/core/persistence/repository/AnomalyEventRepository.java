package com.voltflow.core.persistence.repository;

import com.voltflow.core.domain.AnomalyStatus;
import com.voltflow.core.persistence.entity.AnomalyEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnomalyEventRepository extends JpaRepository<AnomalyEventEntity, Long> {
    Optional<AnomalyEventEntity> findFirstByApplianceIdAndStatus(Long applianceId, AnomalyStatus status);
    List<AnomalyEventEntity> findByHomeIdOrderByDetectedAtDesc(Long homeId);
}
