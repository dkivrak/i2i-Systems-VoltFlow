package com.voltwise.core.persistence.repository;

import com.voltwise.core.domain.TariffState;
import com.voltwise.core.persistence.entity.TariffChangeEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TariffChangeEventRepository extends JpaRepository<TariffChangeEventEntity, Long> {
    boolean existsByHomeIdAndBillingPeriodAndNewTariff(Long homeId, LocalDate period, TariffState state);
    List<TariffChangeEventEntity> findByHomeIdOrderByChangedAtDesc(Long homeId);
}
