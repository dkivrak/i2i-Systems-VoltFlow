package com.voltflow.core.snapshot;

import com.voltflow.core.live.SnapshotWindow;
import com.voltflow.core.persistence.entity.ApplianceEntity;
import com.voltflow.core.persistence.entity.ConsumptionSnapshotEntity;
import com.voltflow.core.persistence.entity.HomeEntity;
import com.voltflow.core.persistence.repository.ApplianceRepository;
import com.voltflow.core.persistence.repository.ConsumptionSnapshotRepository;
import com.voltflow.core.persistence.repository.HomeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;

@Component
public class SnapshotWriter {
    private final HomeRepository homes;
    private final ApplianceRepository appliances;
    private final ConsumptionSnapshotRepository snapshots;

    public SnapshotWriter(HomeRepository homes, ApplianceRepository appliances,
                          ConsumptionSnapshotRepository snapshots) {
        this.homes = homes;
        this.appliances = appliances;
        this.snapshots = snapshots;
    }

    /**
     * Persists one atomically rotated home window. A false return means that exact home period already exists.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean persist(CapturedSnapshot captured) {
        if (alreadyPersisted(captured)) {
            return false;
        }

        HomeEntity home = homes.findById(captured.homeId())
                .orElseThrow(() -> new IllegalStateException("Home disappeared during snapshot: " + captured.homeId()));
        var entities = new ArrayList<ConsumptionSnapshotEntity>();
        entities.add(entity(home, null, captured.periodStart(), captured.periodEnd(), captured.homeWindow()));

        captured.applianceWindows().forEach((applianceId, window) -> {
            if (!window.hasSamples()) {
                return;
            }
            ApplianceEntity appliance = appliances.findById(applianceId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Appliance disappeared during snapshot: " + applianceId));
            if (!captured.homeId().equals(appliance.getHome().getId())) {
                throw new IllegalStateException("Appliance does not belong to captured home: " + applianceId);
            }
            Instant applianceStart = window.startedAt() == null ? captured.periodStart() : window.startedAt();
            entities.add(entity(home, appliance, applianceStart, captured.periodEnd(), window));
        });
        snapshots.saveAll(entities);
        return true;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public boolean alreadyPersisted(CapturedSnapshot captured) {
        return snapshots.existsByHomeIdAndApplianceIsNullAndPeriodStartAndPeriodEnd(
                captured.homeId(), captured.periodStart(), captured.periodEnd());
    }

    private ConsumptionSnapshotEntity entity(HomeEntity home, ApplianceEntity appliance, Instant start, Instant end,
                                             SnapshotWindow window) {
        ConsumptionSnapshotEntity entity = new ConsumptionSnapshotEntity();
        entity.setHome(home);
        entity.setAppliance(appliance);
        entity.setPeriodStart(start);
        entity.setPeriodEnd(end);
        entity.setEnergyKwh(window.energyKwh());
        entity.setAveragePowerWatts(window.averagePowerWatts());
        entity.setMaximumPowerWatts(window.maximumPowerWatts());
        entity.setCost(window.cost());
        entity.setCreatedAt(Instant.now());
        return entity;
    }
}
