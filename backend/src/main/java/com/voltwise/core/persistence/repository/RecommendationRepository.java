package com.voltwise.core.persistence.repository;

import com.voltwise.core.domain.TriggerType;
import com.voltwise.core.persistence.entity.RecommendationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationRepository extends JpaRepository<RecommendationEntity, Long> {
    boolean existsByHomeIdAndTriggerTypeAndTriggerReferenceId(Long homeId, TriggerType triggerType, Long referenceId);
    Page<RecommendationEntity> findByHomeIdOrderByCreatedAtDesc(Long homeId, Pageable pageable);
}
