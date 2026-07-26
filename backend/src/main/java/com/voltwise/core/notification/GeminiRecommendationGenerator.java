package com.voltwise.core.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltwise.core.config.VoltWiseProperties;
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
    private final VoltWiseProperties properties;
    private final RestClient restClient;

    public GeminiRecommendationGenerator(VoltWiseProperties properties, ObjectMapper objectMapper) {
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
        String model = "gemini-3.1-flash-lite";
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
            return new GeneratedRecommendation(cleanMarkdown(text), model, false);
        } catch (Exception ex) {
            // Do not log response/request text: transport exceptions may embed credentials or prompt content.
            log.warn("Gemini recommendation unavailable; deterministic fallback selected (failureType={})",
                    ex.getClass().getSimpleName());
            return fallback(model);
        }
    }

    private static String cleanMarkdown(String input) {
        if (input == null) return "";
        return input.replaceAll("\\*\\*", "")
                    .replaceAll("\\*", "")
                    .replaceAll("`", "")
                    .replaceAll("#+\\s*", "")
                    .replaceAll("_", "")
                    .strip();
    }

    String prompt(RecommendationContext context) {
        String anomalies = context.anomalousAppliances().isEmpty() ? "Yok" : context.anomalousAppliances().stream()
                .map(a -> "%s (Güncel %s W, Güvenli Sınır %s W)".formatted(
                        a.name(), a.currentPowerWatts().toPlainString(), a.safePowerLimitWatts().toPlainString()))
                .reduce((a, b) -> a + "; " + b).orElse("Yok");

        if (context.triggerType() == com.voltwise.core.domain.TriggerType.APPLIANCE_ANOMALY) {
            return """
                    Sen bir akıllı ev enerji uzmanısın. Aşağıdaki cihaz anomalisi durumu için KESİNLİKLE TÜRKÇE, tam olarak 2-3 cümlelik, yapıcı ve cihaz-odaklı bir tasarruf ve güvenlik tavsiyesi yaz.
                    Ev Adı: %s
                    Tetikleyici: Cihaz Anomalisi (Üst üste 3+ kez güvenli Watt sınırı aşıldı)
                    Sorunlu Cihazlar: %s
                    Aktif Tarife: %s
                    Lütfen 2-3 cümleyi geçme, net ve kişiselleştirilmiş bir Türkçe tavsiye ver.
                    """.formatted(context.homeName(), anomalies, context.tariffState());
        }

        return """
                Sen bir akıllı ev enerji uzmanısın. Aşağıdaki bütçe ve tarife durumu için KESİNLİKLE TÜRKÇE, tam olarak 2-3 cümlelik, yapıcı ve kişiselleştirilmiş bir enerji tasarrufu tavsiyesi yaz.
                Ev Adı: %s
                Tetikleyici: %s
                Birikmiş Tüketim: %s kWh
                Güncel Maliyet: %s TL
                Aylık Bütçe: %s TL
                Bütçe Kullanım Oranı: %s%%
                Aktif Tarife: %s
                Anormal Cihazlar: %s
                Lütfen 2-3 cümleyi geçme, net ve uygulanabilir bir Türkçe tasarruf tavsiyesi ver.
                """.formatted(context.homeName(), context.triggerType(),
                context.accumulatedEnergyKwh().toPlainString(), context.currentCost().toPlainString(),
                context.monthlyBudget().toPlainString(), context.budgetUsagePercent().toPlainString(),
                context.tariffState(), anomalies);
    }

    private GeneratedRecommendation fallback(String model) {
        return new GeneratedRecommendation(FALLBACK_TEXT, model, true);
    }

}
