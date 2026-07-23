package com.voltwise.core.persistence.entity;

import com.voltwise.core.domain.ApplianceType;
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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "appliances")
public class ApplianceEntity extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_id", nullable = false)
    private HomeEntity home;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ApplianceType type;

    @Column(name = "safe_power_limit_watts", nullable = false, precision = 19, scale = 3)
    private BigDecimal safePowerLimitWatts;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public HomeEntity getHome() { return home; }
    public void setHome(HomeEntity home) { this.home = home; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ApplianceType getType() { return type; }
    public void setType(ApplianceType type) { this.type = type; }

    public BigDecimal getSafePowerLimitWatts() { return safePowerLimitWatts; }
    public void setSafePowerLimitWatts(BigDecimal safePowerLimitWatts) { this.safePowerLimitWatts = safePowerLimitWatts; }
}
