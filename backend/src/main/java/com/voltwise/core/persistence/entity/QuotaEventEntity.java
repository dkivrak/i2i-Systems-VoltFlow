package com.voltwise.core.persistence.entity;

import com.voltwise.core.domain.QuotaThreshold;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "quota_events", uniqueConstraints = @UniqueConstraint(name = "uk_quota_home_period_threshold", columnNames = {"home_id", "billing_period", "threshold"}))
public class QuotaEventEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "home_id", nullable = false)
    private HomeEntity home;
    @Column(name = "billing_period", nullable = false)
    private LocalDate billingPeriod;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private QuotaThreshold threshold;
    @Column(name = "usage_percent", nullable = false, precision = 12, scale = 4)
    private BigDecimal usagePercent;
    @Column(name = "current_cost", nullable = false, precision = 19, scale = 6)
    private BigDecimal currentCost;
    @Column(name = "monthly_budget", nullable = false, precision = 19, scale = 4)
    private BigDecimal monthlyBudget;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
