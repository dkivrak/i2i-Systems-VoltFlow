package com.voltwise.core.registration;

import com.voltwise.core.api.HomeDtos.ApplianceResponse;
import com.voltwise.core.api.HomeDtos.CreateHomeRequest;
import com.voltwise.core.api.HomeDtos.HomeResponse;
import com.voltwise.core.domain.TariffState;
import com.voltwise.core.config.VoltWiseProperties;
import com.voltwise.core.event.AssetRegistrationEvent;
import com.voltwise.core.live.LiveStateInitializer;
import com.voltwise.core.persistence.entity.ApplianceEntity;
import com.voltwise.core.persistence.entity.BillingLedgerEntity;
import com.voltwise.core.persistence.entity.HomeEntity;
import com.voltwise.core.persistence.repository.BillingLedgerRepository;
import com.voltwise.core.persistence.repository.HomeRepository;
import com.voltwise.core.registration.outbox.RegistrationOutboxDispatcher;
import com.voltwise.core.registration.outbox.RegistrationOutboxPersistenceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

@Service
public class HomeService {
    private static final Logger log = LoggerFactory.getLogger(HomeService.class);
    private final HomeRepository homes;
    private final BillingLedgerRepository ledgers;
    private final RegistrationOutboxPersistenceService registrationOutbox;
    private final RegistrationOutboxDispatcher outboxDispatcher;
    private final LiveStateInitializer liveStateInitializer;
    private final VoltWiseProperties properties;

    public HomeService(HomeRepository homes, BillingLedgerRepository ledgers,
                       RegistrationOutboxPersistenceService registrationOutbox,
                       RegistrationOutboxDispatcher outboxDispatcher,
                       LiveStateInitializer liveStateInitializer, VoltWiseProperties properties) {
        this.homes = homes;
        this.ledgers = ledgers;
        this.registrationOutbox = registrationOutbox;
        this.outboxDispatcher = outboxDispatcher;
        this.liveStateInitializer = liveStateInitializer;
        this.properties = properties;
    }

    @Transactional
    public HomeResponse create(CreateHomeRequest request) {
        HomeEntity home = new HomeEntity();
        home.setName(request.name().trim());
        home.setContactEmail(request.contactEmail().trim().toLowerCase(java.util.Locale.ROOT));
        home.setMonthlyBudget(valueOrDefault(request.monthlyBudget(),
                properties.getBilling().getDefaultMonthlyBudget()));
        home.setNormalTariffPerKwh(valueOrDefault(request.normalTariffPerKwh(),
                properties.getBilling().getNormalTariffPerKwh()));
        home.setPenaltyMultiplier(valueOrDefault(request.penaltyMultiplier(),
                properties.getBilling().getPenaltyTariffMultiplier()));
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
                @Override public void afterCommit() {
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

    private AssetRegistrationEvent toEvent(HomeEntity home) {
        var appliances = home.getAppliances().stream().map(a -> new AssetRegistrationEvent.RegisteredAppliance(
                a.getId(), a.getName(), a.getType(), a.getSafePowerLimitWatts())).toList();
        return new AssetRegistrationEvent(UUID.randomUUID(), 1, "HOME_REGISTERED", Instant.now(),
                home.getId(), home.getName(), appliances);
    }

    private HomeResponse toResponse(HomeEntity home) {
        return new HomeResponse(home.getId(), home.getName(), home.getContactEmail(), home.getMonthlyBudget(),
                home.getNormalTariffPerKwh(), home.getPenaltyMultiplier(), home.getCreatedAt(),
                home.getAppliances().stream().map(a -> new ApplianceResponse(a.getId(), a.getName(), a.getType(),
                        a.getSafePowerLimitWatts())).toList());
    }

    private HomeResponse toResponseWithoutAppliances(HomeEntity home) {
        return new HomeResponse(home.getId(), home.getName(), home.getContactEmail(), home.getMonthlyBudget(),
                home.getNormalTariffPerKwh(), home.getPenaltyMultiplier(), home.getCreatedAt(), java.util.List.of());
    }

    private LocalDate currentBillingPeriod() {
        return LocalDate.now(ZoneOffset.UTC).with(TemporalAdjusters.firstDayOfMonth());
    }

    private BigDecimal valueOrDefault(BigDecimal supplied, BigDecimal configuredDefault) {
        return supplied == null ? configuredDefault : supplied;
    }
}
