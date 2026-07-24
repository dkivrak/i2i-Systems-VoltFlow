package com.voltflow.core.persistence.repository;

import com.voltflow.core.persistence.entity.BillingLedgerEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface BillingLedgerRepository extends JpaRepository<BillingLedgerEntity, Long> {
    Optional<BillingLedgerEntity> findByHomeIdAndBillingPeriod(Long homeId, LocalDate billingPeriod);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BillingLedgerEntity b where b.home.id = :homeId and b.billingPeriod = :period")
    Optional<BillingLedgerEntity> findForUpdate(@Param("homeId") Long homeId, @Param("period") LocalDate period);
}
