package com.voltwise.core.live;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

@Component
@ConditionalOnProperty(prefix = "voltwise.ignite", name = "enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryLiveStateStore implements LiveStateStore {
    private final ConcurrentHashMap<Long, HomeLiveState> states = new ConcurrentHashMap<>();

    @Override public Optional<HomeLiveState> get(Long homeId) { return Optional.ofNullable(states.get(homeId)); }
    @Override public Collection<HomeLiveState> getAll() { return List.copyOf(states.values()); }
    @Override public HomeLiveState putIfAbsent(Long homeId, HomeLiveState initialState) {
        HomeLiveState existing = states.putIfAbsent(homeId, initialState);
        return existing == null ? initialState : existing;
    }
    @Override public HomeLiveState update(Long homeId, UnaryOperator<HomeLiveState> updater) {
        return states.compute(homeId, (id, current) -> {
            if (current == null) throw new IllegalStateException("Live state is not initialized for home " + id);
            return updater.apply(current);
        });
    }
    @Override public boolean compareAndSet(Long homeId, HomeLiveState expected, HomeLiveState replacement) {
        return states.replace(homeId, expected, replacement);
    }
    @Override public boolean remove(Long homeId) {
        return states.remove(homeId) != null;
    }
}
