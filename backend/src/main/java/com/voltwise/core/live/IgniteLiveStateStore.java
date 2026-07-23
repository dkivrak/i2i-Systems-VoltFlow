package com.voltwise.core.live;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltwise.core.config.VoltWiseProperties;
import jakarta.annotation.PreDestroy;
import org.apache.ignite.Ignition;
import org.apache.ignite.client.ClientCache;
import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.configuration.ClientConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.function.UnaryOperator;

@Component
@ConditionalOnProperty(prefix = "voltwise.ignite", name = "enabled", havingValue = "true")
public class IgniteLiveStateStore implements LiveStateStore {
    private final IgniteClient client;
    private final ClientCache<Long, String> cache;
    private final ObjectMapper objectMapper;
    private static final int MAX_CAS_ATTEMPTS = 100;

    public IgniteLiveStateStore(VoltWiseProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        String[] addresses = properties.getIgnite().getAddresses().split(",");
        this.client = Ignition.startClient(new ClientConfiguration().setAddresses(addresses));
        this.cache = client.getOrCreateCache(properties.getIgnite().getCacheName());
    }

    @Override
    public Optional<HomeLiveState> get(Long homeId) {
        String value = cache.get(homeId);
        return value == null ? Optional.empty() : Optional.of(read(value));
    }

    @Override
    public Collection<HomeLiveState> getAll() {
        Collection<HomeLiveState> values = new ArrayList<>();
        try (var cursor = cache.query(new org.apache.ignite.cache.query.ScanQuery<Long, String>())) {
            cursor.forEach(entry -> values.add(read(entry.getValue())));
        }
        return values;
    }

    @Override
    public HomeLiveState putIfAbsent(Long homeId, HomeLiveState initialState) {
        String previous = cache.getAndPutIfAbsent(homeId, write(initialState));
        return previous == null ? initialState : read(previous);
    }

    @Override
    public HomeLiveState update(Long homeId, UnaryOperator<HomeLiveState> updater) {
        for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
            String currentJson = cache.get(homeId);
            if (currentJson == null) {
                throw new IllegalStateException("Live state is not initialized for home " + homeId);
            }
            HomeLiveState current = read(currentJson);
            HomeLiveState updated = updater.apply(current);
            if (cache.replace(homeId, currentJson, write(updated))) return updated;
        }
        throw new IllegalStateException("Live state update contention exceeded retry limit for home " + homeId);
    }

    @Override
    public boolean compareAndSet(Long homeId, HomeLiveState expected, HomeLiveState replacement) {
        for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
            String currentJson = cache.get(homeId);
            if (currentJson == null) return false;
            HomeLiveState current = read(currentJson);
            if (!current.equals(expected)) return false;
            if (cache.replace(homeId, currentJson, write(replacement))) return true;
        }
        throw new IllegalStateException("Live state compare-and-set contention exceeded retry limit for home " + homeId);
    }

    @Override
    public boolean remove(Long homeId) {
        return cache.remove(homeId);
    }

    private String write(HomeLiveState state) {
        try { return objectMapper.writeValueAsString(state); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot serialize live state", ex); }
    }

    private HomeLiveState read(String value) {
        try { return objectMapper.readValue(value, HomeLiveState.class); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot deserialize live state", ex); }
    }

    @PreDestroy
    void close() { client.close(); }
}
