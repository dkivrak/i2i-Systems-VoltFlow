package com.voltflow.core.persistence.repository;

import com.voltflow.core.persistence.entity.ConsumptionSnapshotEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ConsumptionSnapshotRepository extends JpaRepository<ConsumptionSnapshotEntity, Long> {
    Page<ConsumptionSnapshotEntity> findByHomeIdAndApplianceIsNullAndPeriodStartGreaterThanEqualAndPeriodEndLessThanEqualOrderByPeriodStartAsc(
            Long homeId, Instant from, Instant to, Pageable pageable);
    boolean existsByHomeIdAndApplianceIsNullAndPeriodStartAndPeriodEnd(Long homeId, Instant start, Instant end);
    List<ConsumptionSnapshotEntity> findByHomeIdAndApplianceIsNullAndPeriodStartGreaterThanEqualAndPeriodEndLessThanEqualOrderByPeriodStartAsc(
            Long homeId, Instant from, Instant to);
    List<ConsumptionSnapshotEntity> findByHomeIdAndApplianceIsNullAndPeriodStartLessThanAndPeriodEndGreaterThanOrderByPeriodStartAsc(
            Long homeId, Instant to, Instant from);
}
