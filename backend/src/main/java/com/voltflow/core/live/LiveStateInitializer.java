package com.voltflow.core.live;

import com.voltflow.core.domain.TariffState;
import com.voltflow.core.domain.AnomalyStatus;
import com.voltflow.core.domain.ApplianceHealthStatus;
import com.voltflow.core.persistence.entity.BillingLedgerEntity;
import com.voltflow.core.persistence.entity.HomeEntity;
import com.voltflow.core.persistence.repository.BillingLedgerRepository;
import com.voltflow.core.persistence.repository.HomeRepository;
import com.voltflow.core.persistence.repository.AnomalyEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;

@Service
public class LiveStateInitializer {
    private static final Logger log = LoggerFactory.getLogger(LiveStateInitializer.class);
    private final LiveStateStore store;
    private final HomeRepository homes;
    private final BillingLedgerRepository ledgers;
    private final AnomalyEventRepository anomalies;

    public LiveStateInitializer(LiveStateStore store, HomeRepository homes, BillingLedgerRepository ledgers,
                                AnomalyEventRepository anomalies) {
        this.store = store;
        this.homes = homes;
        this.ledgers = ledgers;
        this.anomalies = anomalies;
    }

    @Transactional(readOnly = true)
    public HomeLiveState ensure(Long homeId) {
        return store.get(homeId).orElseGet(() -> initialize(homeId));
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public HomeLiveState initializeRegistered(Long homeId) {
        return ensure(homeId);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void rebuildAfterStartup() {
        homes.findAll().forEach(home -> ensure(home.getId()));
        log.info("Live-state startup rebuild checked {} registered homes", homes.count());
    }

    private HomeLiveState initialize(Long homeId) {
        HomeEntity home = homes.findDetailedById(homeId)
                .orElseThrow(() -> new LiveStateNotFoundException("Home not found: " + homeId));
        LocalDate period = LocalDate.now(ZoneOffset.UTC).with(TemporalAdjusters.firstDayOfMonth());
        BillingLedgerEntity ledger = ledgers.findByHomeIdAndBillingPeriod(homeId, period).orElse(null);
        BigDecimal energy = ledger == null ? BigDecimal.ZERO : ledger.getAccumulatedEnergyKwh();
        BigDecimal cost = ledger == null ? BigDecimal.ZERO : ledger.getAccumulatedCost();
        TariffState tariff = ledger == null ? TariffState.NORMAL : ledger.getTariffState();
        BigDecimal usage = cost.multiply(BigDecimal.valueOf(100))
                .divide(home.getMonthlyBudget(), 4, java.math.RoundingMode.HALF_UP);
        var appliances = new LinkedHashMap<Long, ApplianceLiveState>();
        home.getAppliances().forEach(a -> {
            boolean active = anomalies.findFirstByApplianceIdAndStatus(a.getId(), AnomalyStatus.ACTIVE).isPresent();
            ApplianceLiveState empty = ApplianceLiveState.empty(a.getId(), a.getName(), a.getType(), a.getSafePowerLimitWatts());
            appliances.put(a.getId(), active
                    ? new ApplianceLiveState(empty.applianceId(), empty.name(), empty.type(), empty.currentPowerWatts(),
                    empty.accumulatedEnergyKwh(), empty.accumulatedCost(), empty.operatingState(),
                    empty.safePowerLimitWatts(), 3, ApplianceHealthStatus.ANOMALOUS, empty.lastUpdatedAt())
                    : empty);
        });
        return store.putIfAbsent(homeId, new HomeLiveState(homeId, home.getName(), BigDecimal.ZERO, energy, cost,
                home.getMonthlyBudget(), usage, tariff, null, appliances));
    }
}
