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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "billing_ledgers", uniqueConstraints = @UniqueConstraint(name = "uk_billing_home_period", columnNames = {"home_id", "billing_period"}))
public class BillingLedgerEntity extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_id", nullable = false)
    private HomeEntity home;

    @Column(name = "billing_period", nullable = false)
    private LocalDate billingPeriod;

    @Column(name = "accumulated_energy_kwh", nullable = false, precision = 24, scale = 9)
    private BigDecimal accumulatedEnergyKwh = BigDecimal.ZERO;

    @Column(name = "accumulated_cost", nullable = false, precision = 19, scale = 6)
    private BigDecimal accumulatedCost = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "tariff_state", nullable = false, length = 20)
    private TariffState tariffState = TariffState.NORMAL;

    @Version
    private long version;
}
