package com.voltwise.core.persistence.entity;

import com.voltwise.core.domain.AnomalyStatus;
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

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "anomaly_events")
public class AnomalyEventEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "home_id", nullable = false)
    private HomeEntity home;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "appliance_id", nullable = false)
    private ApplianceEntity appliance;
    @Column(name = "measured_power_watts", nullable = false, precision = 19, scale = 3)
    private BigDecimal measuredPowerWatts;
    @Column(name = "safe_power_limit_watts", nullable = false, precision = 19, scale = 3)
    private BigDecimal safePowerLimitWatts;
    @Column(name = "consecutive_breach_count", nullable = false)
    private int consecutiveBreachCount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private AnomalyStatus status;
    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;
    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
