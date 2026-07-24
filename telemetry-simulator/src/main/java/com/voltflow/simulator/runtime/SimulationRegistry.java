package com.voltflow.simulator.runtime;

import com.voltflow.simulator.config.SimulationProperties;
import com.voltflow.simulator.event.AssetRegistrationEvent;
import com.voltflow.simulator.event.RegisteredAppliance;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class SimulationRegistry {

    private final SimulationProperties properties;
    private final ConcurrentMap<ApplianceKey, ApplianceRuntime> appliances = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, String> homeNames = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> processedEventIds = new LinkedHashMap<>();

    public SimulationRegistry(SimulationProperties properties) {
        this.properties = properties;
    }

    public synchronized RegistrationResult register(AssetRegistrationEvent event) {
        if (processedEventIds.containsKey(event.eventId())) {
            return RegistrationResult.duplicate();
        }

        for (RegisteredAppliance appliance : event.appliances()) {
            ApplianceRuntime existing = appliances.get(new ApplianceKey(event.homeId(), appliance.applianceId()));
            if (existing != null && existing.appliance().type() != appliance.type()) {
                throw new IllegalArgumentException("An appliance type cannot change after registration");
            }
        }

        homeNames.put(event.homeId(), event.homeName().trim());
        AtomicInteger added = new AtomicInteger();
        AtomicInteger updated = new AtomicInteger();
        for (RegisteredAppliance appliance : event.appliances()) {
            ApplianceKey key = new ApplianceKey(event.homeId(), appliance.applianceId());
            appliances.compute(key, (ignored, existing) -> {
                if (existing == null) {
                    added.incrementAndGet();
                    return new ApplianceRuntime(
                            event.homeId(),
                            appliance,
                            applianceSeed(properties.getRandomSeed(), event.homeId(), appliance)
                    );
                }
                existing.update(appliance);
                updated.incrementAndGet();
                return existing;
            });
        }
        rememberProcessedEvent(event.eventId());
        return new RegistrationResult(false, added.get(), updated.get());
    }

    public List<ApplianceRuntime> snapshot() {
        List<ApplianceRuntime> result = new ArrayList<>(appliances.values());
        result.sort(Comparator.comparingLong(ApplianceRuntime::homeId)
                .thenComparingLong(runtime -> runtime.appliance().applianceId()));
        return List.copyOf(result);
    }

    public int applianceCount() {
        return appliances.size();
    }

    public String homeName(long homeId) {
        return homeNames.get(homeId);
    }

    private void rememberProcessedEvent(UUID eventId) {
        processedEventIds.put(eventId, Boolean.TRUE);
        int maximumSize = properties.getProcessedEventCacheSize();
        while (processedEventIds.size() > maximumSize) {
            UUID oldest = processedEventIds.keySet().iterator().next();
            processedEventIds.remove(oldest);
        }
    }

    private long applianceSeed(long baseSeed, long homeId, RegisteredAppliance appliance) {
        long value = baseSeed;
        value ^= Long.rotateLeft(homeId, 17);
        value ^= Long.rotateLeft(appliance.applianceId(), 37);
        value ^= (long) appliance.type().ordinal() * 0x9E3779B97F4A7C15L;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private record ApplianceKey(long homeId, long applianceId) {
    }
}
