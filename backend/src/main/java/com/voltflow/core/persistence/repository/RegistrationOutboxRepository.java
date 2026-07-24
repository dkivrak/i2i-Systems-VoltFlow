package com.voltflow.core.persistence.repository;

import com.voltflow.core.persistence.entity.RegistrationOutboxEntity;
import com.voltflow.core.registration.outbox.RegistrationOutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegistrationOutboxRepository extends JpaRepository<RegistrationOutboxEntity, Long> {
    Optional<RegistrationOutboxEntity> findByEventId(UUID eventId);
    List<RegistrationOutboxEntity> findByHomeIdOrderByCreatedAtAsc(Long homeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from RegistrationOutboxEntity o where o.eventId = :eventId")
    Optional<RegistrationOutboxEntity> findByEventIdForUpdate(@Param("eventId") UUID eventId);

    @Query("select o.eventId from RegistrationOutboxEntity o "
            + "where o.status = :status and o.nextAttemptAt <= :now order by o.nextAttemptAt, o.id")
    List<UUID> findDueEventIds(@Param("status") RegistrationOutboxStatus status,
                               @Param("now") Instant now, Pageable pageable);
}
