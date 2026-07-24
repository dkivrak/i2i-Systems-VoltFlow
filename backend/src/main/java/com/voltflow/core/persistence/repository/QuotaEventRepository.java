package com.voltflow.core.persistence.repository;

import com.voltflow.core.domain.QuotaThreshold;
import com.voltflow.core.persistence.entity.QuotaEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface QuotaEventRepository extends JpaRepository<QuotaEventEntity, Long> {
    boolean existsByHomeIdAndBillingPeriodAndThreshold(Long homeId, LocalDate period, QuotaThreshold threshold);
    List<QuotaEventEntity> findByHomeIdOrderByOccurredAtDesc(Long homeId);
}
