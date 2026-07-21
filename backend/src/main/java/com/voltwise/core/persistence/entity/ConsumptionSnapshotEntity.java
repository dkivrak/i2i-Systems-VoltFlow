package com.voltwise.core.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "consumption_snapshots")
public class ConsumptionSnapshotEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "home_id", nullable = false)
    private HomeEntity home;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "appliance_id")
    private ApplianceEntity appliance;
    @Column(name = "period_start", nullable = false)
    private Instant periodStart;
    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;
    @Column(name = "energy_kwh", nullable = false, precision = 24, scale = 9)
    private BigDecimal energyKwh;
    @Column(name = "average_power_watts", nullable = false, precision = 19, scale = 3)
    private BigDecimal averagePowerWatts;
    @Column(name = "maximum_power_watts", nullable = false, precision = 19, scale = 3)
    private BigDecimal maximumPowerWatts;
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal cost;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
