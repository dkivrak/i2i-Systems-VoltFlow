package com.voltwise.core.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "homes")
public class HomeEntity extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "contact_email", nullable = false, length = 320)
    private String contactEmail;

    @Column(name = "monthly_budget", nullable = false, precision = 19, scale = 4)
    private BigDecimal monthlyBudget;

    @Column(name = "normal_tariff_per_kwh", nullable = false, precision = 19, scale = 6)
    private BigDecimal normalTariffPerKwh;

    @Column(name = "penalty_multiplier", nullable = false, precision = 10, scale = 4)
    private BigDecimal penaltyMultiplier;

    @OneToMany(mappedBy = "home", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ApplianceEntity> appliances = new ArrayList<>();

    public void addAppliance(ApplianceEntity appliance) {
        appliance.setHome(this);
        appliances.add(appliance);
    }
}
