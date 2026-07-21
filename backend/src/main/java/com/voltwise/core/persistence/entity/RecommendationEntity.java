package com.voltwise.core.persistence.entity;

import com.voltwise.core.domain.TriggerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "recommendations")
public class RecommendationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "home_id", nullable = false)
    private HomeEntity home;
    @Enumerated(EnumType.STRING) @Column(name = "trigger_type", nullable = false, length = 40)
    private TriggerType triggerType;
    @Column(name = "trigger_reference_id", nullable = false)
    private Long triggerReferenceId;
    @Column(name = "recommendation_text", nullable = false, columnDefinition = "TEXT")
    private String recommendationText;
    @Column(name = "model_name", nullable = false, length = 120)
    private String modelName;
    @Column(name = "fallback_used", nullable = false)
    private boolean fallbackUsed;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
