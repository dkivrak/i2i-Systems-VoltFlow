package com.voltwise.core.live;

import com.voltwise.core.config.VoltWiseProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("liveState")
public class LiveStateHealthIndicator implements HealthIndicator {
    private final LiveStateStore store;
    private final VoltWiseProperties properties;

    public LiveStateHealthIndicator(LiveStateStore store, VoltWiseProperties properties) {
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
