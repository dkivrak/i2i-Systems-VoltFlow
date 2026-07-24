package com.voltflow.core.persistence.repository;

import com.voltflow.core.domain.TriggerType;
import com.voltflow.core.persistence.entity.RecommendationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationRepository extends JpaRepository<RecommendationEntity, Long> {
    boolean existsByHomeIdAndTriggerTypeAndTriggerReferenceId(Long homeId, TriggerType triggerType, Long referenceId);
    Page<RecommendationEntity> findByHomeIdOrderByCreatedAtDesc(Long homeId, Pageable pageable);
}
