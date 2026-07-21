package com.voltwise.core.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltwise.core.config.VoltWiseProperties;
import com.voltwise.core.domain.TariffState;
import com.voltwise.core.domain.TriggerType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiRecommendationGeneratorTest {
    @Test
    void usesDeterministicTurkishFallbackWhenApiKeyIsAbsent() {
        VoltWiseProperties properties = new VoltWiseProperties();
        properties.getGemini().setApiKey("");
        var generator = new GeminiRecommendationGenerator(properties, new ObjectMapper());
        var context = new RecommendationContext(1L, "Ev", "owner@example.com", TriggerType.QUOTA_80,
                new BigDecimal("10"), new BigDecimal("80"), new BigDecimal("100"),
                new BigDecimal("80"), TariffState.NORMAL, List.of());
        var result = generator.generate(context);
        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.text()).isEqualTo(GeminiRecommendationGenerator.FALLBACK_TEXT)
                .contains("Enerji", "Lütfen");
    }

    @Test
    void promptContainsStructuredOperationalContext() {
        VoltWiseProperties properties = new VoltWiseProperties();
        var generator = new GeminiRecommendationGenerator(properties, new ObjectMapper());
        var context = new RecommendationContext(1L, "Kadıköy", "owner@example.com",
                TriggerType.APPLIANCE_ANOMALY, new BigDecimal("12.3"), new BigDecimal("101"),
                new BigDecimal("100"), new BigDecimal("101"), TariffState.PENALTY,
                List.of(new RecommendationContext.AnomalousAppliance("Kettle", new BigDecimal("2500"),
                        new BigDecimal("2200"))));
        assertThat(generator.prompt(context)).contains("Kadıköy", "PENALTY", "Kettle", "2500 W", "2200 W");
    }
}
