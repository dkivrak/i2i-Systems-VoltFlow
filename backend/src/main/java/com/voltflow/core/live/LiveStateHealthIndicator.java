package com.voltflow.core.live;

import com.voltflow.core.config.VoltFlowProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("liveState")
public class LiveStateHealthIndicator implements HealthIndicator {
    private final LiveStateStore store;
    private final VoltFlowProperties properties;

    public LiveStateHealthIndicator(LiveStateStore store, VoltFlowProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    @Override
    public Health health() {
        try {
            int homes = store.getAll().size();
            return Health.up()
                    .withDetail("provider", properties.getIgnite().isEnabled() ? "Apache Ignite" : "in-memory")
                    .withDetail("homes", homes)
                    .build();
        } catch (Exception ex) {
            return Health.down(ex).withDetail("provider", "Apache Ignite").build();
        }
    }
}
