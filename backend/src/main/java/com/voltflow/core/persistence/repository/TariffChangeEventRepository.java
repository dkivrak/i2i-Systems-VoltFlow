package com.voltflow.core.persistence.repository;

import com.voltflow.core.domain.TariffState;
import com.voltflow.core.persistence.entity.TariffChangeEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TariffChangeEventRepository extends JpaRepository<TariffChangeEventEntity, Long> {
    boolean existsByHomeIdAndBillingPeriodAndNewTariff(Long homeId, LocalDate period, TariffState state);
    List<TariffChangeEventEntity> findByHomeIdOrderByChangedAtDesc(Long homeId);
}
