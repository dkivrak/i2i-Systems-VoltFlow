package com.voltflow.core.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltflow.core.config.VoltFlowProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class GeminiRecommendationGenerator implements RecommendationGenerator {
    public static final String FALLBACK_TEXT = "Enerji kullanımınız tanımlanan sınıra ulaşmış veya bir cihazda olağan dışı tüketim algılanmıştır. Lütfen cihazlarınızı ve güncel tüketim değerlerinizi kontrol ediniz.";
    private static final Logger log = LoggerFactory.getLogger(GeminiRecommendationGenerator.class);
    private final VoltFlowProperties properties;
    private final RestClient restClient;

    public GeminiRecommendationGenerator(VoltFlowProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getGemini().getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getGemini().getReadTimeoutMs());
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public GeneratedRecommendation generate(RecommendationContext context) {
        String model = properties.getGemini().getModel();
        if (!StringUtils.hasText(properties.getGemini().getApiKey())) {
            return fallback(model);
        }
        try {
            Map<String, Object> body = Map.of("contents", List.of(Map.of("parts",
                    List.of(Map.of("text", prompt(context))))));
            JsonNode response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", properties.getGemini().getApiKey())
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String text = response == null ? null : response.at("/candidates/0/content/parts/0/text").asText(null);
            if (!StringUtils.hasText(text)) throw new IllegalStateException("Gemini returned no recommendation text");
            return new GeneratedRecommendation(text.strip(), model, false);
        } catch (Exception ex) {
            // Do not log response/request text: transport exceptions may embed credentials or prompt content.
            log.warn("Gemini recommendation unavailable; deterministic fallback selected (failureType={})",
                    ex.getClass().getSimpleName());
            return fallback(model);
        }
    }

    String prompt(RecommendationContext context) {
        String anomalies = context.anomalousAppliances().isEmpty() ? "Yok" : context.anomalousAppliances().stream()
                .map(a -> "%s: güncel %s W, güvenli sınır %s W".formatted(
                        a.name(), a.currentPowerWatts().toPlainString(), a.safePowerLimitWatts().toPlainString()))
                .reduce((a, b) -> a + "; " + b).orElse("Yok");
        return """
                Türkçe, kısa ve uygulanabilir bir ev enerji tasarrufu önerisi üret.
                Ev: %s
                Tetikleyici: %s
                Birikmiş tüketim: %s kWh
                Güncel maliyet: %s TL
                Aylık bütçe: %s TL
                Bütçe kullanım oranı: %s%%
                Aktif tarife: %s
                Anormal cihazlar ve sınırlar: %s
                En fazla 4 madde kullan; ölçümleri açıkla, güvenliği öncele ve uydurma veri ekleme.
                """.formatted(context.homeName(), context.triggerType(),
                context.accumulatedEnergyKwh().toPlainString(), context.currentCost().toPlainString(),
                context.monthlyBudget().toPlainString(), context.budgetUsagePercent().toPlainString(),
                context.tariffState(), anomalies);
    }

    private GeneratedRecommendation fallback(String model) {
        return new GeneratedRecommendation(FALLBACK_TEXT, model, true);
    }

}
