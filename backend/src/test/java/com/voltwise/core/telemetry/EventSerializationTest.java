package com.voltwise.core.telemetry;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.voltwise.core.domain.ApplianceType;
import com.voltwise.core.domain.OperatingState;
import com.voltwise.core.event.AssetRegistrationEvent;
import com.voltwise.core.event.TelemetryEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventSerializationTest {
    private final com.fasterxml.jackson.databind.ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void telemetryRoundTripsWithoutTypeHeaders() throws Exception {
        var event = new TelemetryEvent(UUID.randomUUID(), 1, "APPLIANCE_TELEMETRY_RECORDED",
                Instant.parse("2026-07-21T12:00:01Z"), 1L, 10L, ApplianceType.KETTLE,
                new BigDecimal("1850.4"), OperatingState.ON);
        String json = mapper.writeValueAsString(event);
        assertThat(json).contains("\"eventVersion\":1", "\"applianceType\":\"KETTLE\"");
        assertThat(mapper.readValue(json, TelemetryEvent.class)).isEqualTo(event);
    }

    @Test
    void registrationUsesStableEnvelopeAndApplianceShape() throws Exception {
        var event = new AssetRegistrationEvent(UUID.randomUUID(), 1, "HOME_REGISTERED", Instant.now(),
                1L, "Kadikoy Home", List.of(new AssetRegistrationEvent.RegisteredAppliance(
                        10L, "Kitchen Kettle", ApplianceType.KETTLE, new BigDecimal("2200"))));
        String json = mapper.writeValueAsString(event);
        assertThat(json).contains("\"eventType\":\"HOME_REGISTERED\"", "\"safePowerLimitWatts\":2200");
        assertThat(mapper.readValue(json, AssetRegistrationEvent.class)).isEqualTo(event);
    }
}
