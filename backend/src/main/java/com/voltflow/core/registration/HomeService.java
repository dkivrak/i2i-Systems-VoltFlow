package com.voltflow.core.registration;

import com.voltflow.core.api.HomeDtos.ApplianceResponse;
import com.voltflow.core.api.HomeDtos.CreateHomeRequest;
import com.voltflow.core.api.HomeDtos.HomeResponse;
import com.voltflow.core.config.VoltFlowProperties;
import com.voltflow.core.domain.ApplianceType;
import com.voltflow.core.domain.TariffState;
import com.voltflow.core.event.AssetRegistrationEvent;
import com.voltflow.core.live.LiveStateInitializer;
import com.voltflow.core.persistence.entity.ApplianceEntity;
import com.voltflow.core.persistence.entity.BillingLedgerEntity;
import com.voltflow.core.persistence.entity.HomeEntity;
import com.voltflow.core.persistence.repository.BillingLedgerRepository;
import com.voltflow.core.persistence.repository.HomeRepository;
import com.voltflow.core.registration.outbox.RegistrationOutboxDispatcher;
import com.voltflow.core.registration.outbox.RegistrationOutboxPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.UUID;

import com.voltflow.core.live.ApplianceLiveState;
import com.voltflow.core.live.HomeLiveState;
import com.voltflow.core.live.LiveStateStore;
import com.voltflow.core.persistence.repository.ApplianceRepository;

import java.util.HashMap;

@Service
public class HomeService {
    private static final Logger log = LoggerFactory.getLogger(HomeService.class);

    private static final Map<ApplianceType, WattBounds> SAFE_LIMIT_BOUNDS = Map.of(
            ApplianceType.REFRIGERATOR, new WattBounds(new BigDecimal("1"), new BigDecimal("10000000")),
            ApplianceType.KETTLE, new WattBounds(new BigDecimal("1"), new BigDecimal("10000000")),
            ApplianceType.OVEN, new WattBounds(new BigDecimal("1"), new BigDecimal("10000000")),
            ApplianceType.TELEVISION, new WattBounds(new BigDecimal("1"), new BigDecimal("10000000")),
            ApplianceType.WASHING_MACHINE, new WattBounds(new BigDecimal("1"), new BigDecimal("10000000")),
            ApplianceType.AIR_CONDITIONER, new WattBounds(new BigDecimal("1"), new BigDecimal("10000000")),
            ApplianceType.MICROWAVE, new WattBounds(new BigDecimal("1"), new BigDecimal("10000000")),
            ApplianceType.LAMP, new WattBounds(new BigDecimal("0.1"), new BigDecimal("10000000")),
            ApplianceType.COMPUTER, new WattBounds(new BigDecimal("1"), new BigDecimal("10000000"))
    );

    private final HomeRepository homes;
    private final ApplianceRepository appliances;
    private final BillingLedgerRepository ledgers;
    private final RegistrationOutboxPersistenceService registrationOutbox;
    private final RegistrationOutboxDispatcher outboxDispatcher;
    private final LiveStateInitializer liveStateInitializer;
    private final LiveStateStore liveStateStore;
    private final VoltFlowProperties properties;

    public HomeService(HomeRepository homes, ApplianceRepository appliances, BillingLedgerRepository ledgers,
                       RegistrationOutboxPersistenceService registrationOutbox,
                       RegistrationOutboxDispatcher outboxDispatcher,
                       LiveStateInitializer liveStateInitializer, LiveStateStore liveStateStore,
                       VoltFlowProperties properties) {
        this.homes = homes;
        this.appliances = appliances;
        this.ledgers = ledgers;
        this.registrationOutbox = registrationOutbox;
        this.outboxDispatcher = outboxDispatcher;
        this.liveStateInitializer = liveStateInitializer;
        this.liveStateStore = liveStateStore;
        this.properties = properties;
    }

    @Transactional
    public HomeResponse create(CreateHomeRequest request) {
        validateAppliancePowerLimits(request);

        HomeEntity home = new HomeEntity();
        home.setName(request.name().trim());
        home.setCity(request.city() != null && !request.city().isBlank() ? request.city().trim() : "İstanbul");
        home.setContactEmail(request.contactEmail().trim().toLowerCase(java.util.Locale.ROOT));

        home.setMonthlyBudget(valueOrDefault(request.monthlyBudget(), properties.getBilling().getDefaultMonthlyBudget()));
        home.setNormalTariffPerKwh(valueOrDefault(request.normalTariffPerKwh(), properties.getBilling().getNormalTariffPerKwh()));
        home.setPenaltyMultiplier(valueOrDefault(request.penaltyMultiplier(), properties.getBilling().getPenaltyTariffMultiplier()));

        request.appliances().forEach(item -> {
            ApplianceEntity appliance = new ApplianceEntity();
            appliance.setName(item.name().trim());
            appliance.setType(item.type());
            appliance.setSafePowerLimitWatts(item.safePowerLimitWatts());
            home.addAppliance(appliance);
        });

        HomeEntity savedHome = homes.saveAndFlush(home);

        BillingLedgerEntity ledger = new BillingLedgerEntity();
        ledger.setHome(savedHome);
        ledger.setBillingPeriod(currentBillingPeriod());
        ledger.setAccumulatedEnergyKwh(BigDecimal.ZERO);
        ledger.setAccumulatedCost(BigDecimal.ZERO);
        ledger.setTariffState(TariffState.NORMAL);
        ledgers.save(ledger);

        AssetRegistrationEvent event = toEvent(savedHome);
        registrationOutbox.enqueue(savedHome, event);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        liveStateInitializer.initializeRegistered(savedHome.getId());
                    } catch (Exception ex) {
                        log.error("Could not initialize live state after registration homeId={}", savedHome.getId(), ex);
                    }
                    try {
                        outboxDispatcher.requestImmediateDispatch(event.eventId());
                    } catch (Exception ex) {
                        log.error("Could not schedule immediate outbox dispatch homeId={} eventId={}",
                                savedHome.getId(), event.eventId(), ex);
                    }
                }
            });
        } else {
            liveStateInitializer.initializeRegistered(savedHome.getId());
            outboxDispatcher.requestImmediateDispatch(event.eventId());
        }

        return toResponse(savedHome);
    }

    @Transactional(readOnly = true)
    public Page<HomeResponse> list(Pageable pageable) {
        return homes.findAll(pageable).map(this::toResponseWithoutAppliances);
    }

    @Transactional(readOnly = true)
    public HomeEntity requireDetailed(Long id) {
        return homes.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Home not found: " + id));
    }

    @Transactional
    public void deleteHome(Long homeId) {
        HomeEntity home = homes.findById(homeId)
                .orElseThrow(() -> new ResourceNotFoundException("Home not found: " + homeId));
        homes.delete(home);
        liveStateStore.remove(homeId);
        log.info("Home deleted successfully homeId={}", homeId);
    }

    @Transactional
    public void deleteAppliance(Long homeId, Long applianceId) {
        HomeEntity home = homes.findById(homeId)
                .orElseThrow(() -> new ResourceNotFoundException("Home not found: " + homeId));
        ApplianceEntity appliance = appliances.findById(applianceId)
                .orElseThrow(() -> new ResourceNotFoundException("Appliance not found: " + applianceId));

        if (!appliance.getHome().getId().equals(homeId)) {
            throw new IllegalArgumentException("Appliance " + applianceId + " does not belong to home " + homeId);
        }

        home.getAppliances().remove(appliance);
        appliances.delete(appliance);

        liveStateStore.get(homeId).ifPresent(state -> {
            try {
                liveStateStore.update(homeId, current -> {
                    Map<Long, ApplianceLiveState> updated = new HashMap<>(current.appliances());
                    updated.remove(applianceId);
                    return new HomeLiveState(
                            current.homeId(), current.homeName(), current.currentPowerWatts(),
                            current.accumulatedEnergyKwh(), current.currentCost(), current.monthlyBudget(),
                            current.budgetUsagePercent(), current.tariffState(), current.lastUpdatedAt(),
                            updated, current.snapshotWindow()
                    );
                });
            } catch (Exception ex) {
                log.warn("Could not update live state after appliance deletion homeId={} applianceId={}", homeId, applianceId, ex);
            }
        });

        log.info("Appliance deleted successfully homeId={} applianceId={}", homeId, applianceId);
    }

    private void validateAppliancePowerLimits(CreateHomeRequest request) {
        if (request.appliances() == null) return;

        for (int i = 0; i < request.appliances().size(); i++) {
            var app = request.appliances().get(i);
            WattBounds bounds = SAFE_LIMIT_BOUNDS.get(app.type());
            if (bounds != null && app.safePowerLimitWatts() != null) {
                if (app.safePowerLimitWatts().compareTo(bounds.min()) < 0 || app.safePowerLimitWatts().compareTo(bounds.max()) > 0) {
                    throw new IllegalArgumentException(String.format("%s için güvenli güç sınırı %s W ile %s W arasında olmalıdır.",
                            app.type(), bounds.min().toPlainString(), bounds.max().toPlainString()));
                }
            }
        }
    }

    private AssetRegistrationEvent toEvent(HomeEntity home) {
        var appliances = home.getAppliances().stream().map(a -> new AssetRegistrationEvent.RegisteredAppliance(
                a.getId(), a.getName(), a.getType(), a.getSafePowerLimitWatts())).toList();
        return new AssetRegistrationEvent(UUID.randomUUID(), 1, "HOME_REGISTERED", Instant.now(),
                home.getId(), home.getName(), appliances);
    }

    private HomeResponse toResponse(HomeEntity home) {
        return new HomeResponse(home.getId(), home.getName(), home.getCity(), home.getContactEmail(), home.getMonthlyBudget(),
                home.getNormalTariffPerKwh(), home.getPenaltyMultiplier(), home.getCreatedAt(),
                home.getAppliances().stream().map(a -> new ApplianceResponse(a.getId(), a.getName(), a.getType(),
                        a.getSafePowerLimitWatts())).toList());
    }

    private HomeResponse toResponseWithoutAppliances(HomeEntity home) {
        return new HomeResponse(home.getId(), home.getName(), home.getCity(), home.getContactEmail(), home.getMonthlyBudget(),
                home.getNormalTariffPerKwh(), home.getPenaltyMultiplier(), home.getCreatedAt(), java.util.List.of());
    }

    private LocalDate currentBillingPeriod() {
        return LocalDate.now(ZoneOffset.UTC).with(TemporalAdjusters.firstDayOfMonth());
    }

    private BigDecimal valueOrDefault(BigDecimal supplied, BigDecimal configuredDefault) {
        return supplied == null ? configuredDefault : supplied;
    }

    private record WattBounds(BigDecimal min, BigDecimal max) {}
}
