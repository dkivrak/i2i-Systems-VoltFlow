package com.voltflow.core.live;

import java.util.Collection;
import java.util.Optional;
import java.util.function.UnaryOperator;

public interface LiveStateStore {
    Optional<HomeLiveState> get(Long homeId);
    Collection<HomeLiveState> getAll();
    HomeLiveState putIfAbsent(Long homeId, HomeLiveState initialState);
    HomeLiveState update(Long homeId, UnaryOperator<HomeLiveState> updater);
    boolean compareAndSet(Long homeId, HomeLiveState expected, HomeLiveState replacement);
    default boolean remove(Long homeId) { return false; }
}
