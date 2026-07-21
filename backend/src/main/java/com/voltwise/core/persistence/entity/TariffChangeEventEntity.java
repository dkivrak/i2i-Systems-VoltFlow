package com.voltwise.core.persistence.entity;

import com.voltwise.core.domain.TariffState;
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
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tariff_change_events")
public class TariffChangeEventEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "home_id", nullable = false)
    private HomeEntity home;
    @Column(name = "billing_period", nullable = false)
    private LocalDate billingPeriod;
    @Enumerated(EnumType.STRING) @Column(name = "previous_tariff", nullable = false, length = 20)
    private TariffState previousTariff;
    @Enumerated(EnumType.STRING) @Column(name = "new_tariff", nullable = false, length = 20)
    private TariffState newTariff;
    @Column(name = "previous_rate", nullable = false, precision = 19, scale = 6)
    private BigDecimal previousRate;
    @Column(name = "new_rate", nullable = false, precision = 19, scale = 6)
    private BigDecimal newRate;
    @Column(name = "trigger_usage_percent", nullable = false, precision = 12, scale = 4)
    private BigDecimal triggerUsagePercent;
    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;
}
