package com.voltwise.core.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltwise.core.auth.UserContext;
import com.voltwise.core.config.VoltWiseProperties;
import com.voltwise.core.domain.ApplianceHealthStatus;
import com.voltwise.core.live.ApplianceLiveState;
import com.voltwise.core.live.HomeLiveState;
import com.voltwise.core.live.LiveStateInitializer;
import com.voltwise.core.live.LiveStateStore;
import com.voltwise.core.persistence.repository.HomeRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "AI Chat", description = "VoltFlow AI Chatbot Assistant Endpoint")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final RestClient restClient;
    private final VoltWiseProperties properties;
    private final HomeRepository homeRepository;
    private final LiveStateStore liveStateStore;
    private final LiveStateInitializer liveStateInitializer;

    public ChatController(VoltWiseProperties properties, ObjectMapper objectMapper,
                          HomeRepository homeRepository, LiveStateStore liveStateStore,
                          LiveStateInitializer liveStateInitializer) {
        this.properties = properties;
        this.homeRepository = homeRepository;
        this.liveStateStore = liveStateStore;
        this.liveStateInitializer = liveStateInitializer;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getGemini().getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getGemini().getReadTimeoutMs());
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .requestFactory(requestFactory)
                .build();
    }

    public record ChatRequest(String message) {}
    public record ChatResponse(String reply) {}

    @PostMapping
    @Operation(summary = "Send a message to VoltFlow AI assistant")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String userMsg = request != null && request.message() != null ? request.message().trim() : "Merhaba";
        String email = UserContext.getCurrentUserEmail();
        if (!StringUtils.hasText(email)) {
            email = "onur@gmail.com";
        }

        // Build live context for the user's homes
        String liveContext = buildUserLiveContext(email);

        String systemInstruction = """
                Sen VoltFlow akıllı ev enerji yönetim sisteminin yapay zeka asistanısın.
                Adın "VoltFlow AI". Görevin kullanıcıya evinin CANLI GERÇEK enerjisini, maliyetlerini, cihaz anomalilerini ve tasarruf tavsiyelerini açıklamaktır.

                KRİTİK KURAL:
                Sana verilen "CANLI KULLANICI VERİLERİ" bölümündeki GERÇEK SAYISAL VERİLERİ (anlık kW gücü, birikmiş TL maliyeti, bütçeyi, cihaz isimlerini ve anomali durumlarını) KESİNLİKLE KULLAN ve rastgele uydurma rakamlar verme!

                Aşağıda kullanıcının VoltFlow panelindeki CANLI GERÇEK VERİLERİ yer almaktadır:
                === CANLI KULLANICI VERİLERİ ===
                Oturum Açan Kullanıcı: %s
                %s
                =================================

                Kurallar:
                - Her zaman Türkçe cevap ver.
                - Yanıtlarda KESİNLİKLE Markdown veya HTML biçimlendirme karakterleri (yıldız **, *, kare #, alt çizgi _, backtick ` vb.) KULLANMA. Sadece sade ve düz metin (plain text) olarak yaz.
                - Kullanıcının "Bu dönem maliyet", "Anlık toplam güç", "Sağlıklı evler", "Cihaz durumu" gibi sorularına yukarıdaki GERÇEK CANLI VERİLERİ kullanarak 2-4 cümlelik net ve yapıcı cevaplar ver.
                - Rakamları panelde göründüğü gibi (örneğin "₺0,15" veya "2,77 kW") tam doğrulukla ifade et.
                - Enerji tasarrufu ipuçları ver ve samimi bir üslup kullan.
                """.formatted(email, liveContext);

        String model = StringUtils.hasText(properties.getGemini().getModel())
                ? properties.getGemini().getModel()
                : "gemini-3.1-flash-lite";
        String apiKey = StringUtils.hasText(properties.getGemini().getApiKey())
                ? properties.getGemini().getApiKey()
                : "AQ.Ab8RN6L68aFulUrQGBMo1Imkayr2CY3IDFe-GWNaJVFyxDxNyQ";

        try {
            Map<String, Object> body = Map.of(
                    "system_instruction", Map.of("parts", List.of(Map.of("text", systemInstruction))),
                    "contents", List.of(
                            Map.of("role", "user", "parts", List.of(Map.of("text", userMsg)))
                    ),
                    "generationConfig", Map.of(
                            "temperature", 0.5,
                            "maxOutputTokens", 350,
                            "topP", 0.95
                    )
            );

            JsonNode response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            String text = response == null ? null
                    : response.at("/candidates/0/content/parts/0/text").asText(null);

            if (StringUtils.hasText(text)) {
                return new ChatResponse(cleanMarkdown(text));
            }

            log.warn("Gemini chat returned empty text, response={}", response);
            return new ChatResponse("Üzgünüm, şu anda yanıt oluşturamadım. Lütfen tekrar deneyin.");

        } catch (Exception ex) {
            log.warn("Gemini chat API error (type={}): {}", ex.getClass().getSimpleName(), ex.getMessage());
            return new ChatResponse("VoltFlow AI geçici olarak meşgul. Lütfen birkaç saniye sonra tekrar sorunuzu iletin.");
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

    private String buildUserLiveContext(String email) {
        List<Long> homeIds = homeRepository.findIdsByOwnerEmail(email);
        if (homeIds.isEmpty()) {
            return "Kullanıcının henüz kayıtlı bir evi bulunmuyor.";
        }

        StringBuilder sb = new StringBuilder();
        Set<Long> ownedSet = Set.copyOf(homeIds);

        List<HomeLiveState> userStates = liveStateStore.getAll().stream()
                .filter(s -> ownedSet.contains(s.homeId()))
                .toList();

        if (userStates.isEmpty()) {
            for (Long id : homeIds) {
                try {
                    HomeLiveState hls = liveStateInitializer.ensure(id);
                    if (hls != null) {
                        userStates = List.of(hls);
                    }
                } catch (Exception ignored) {}
            }
            if (userStates.isEmpty()) {
                userStates = liveStateStore.getAll().stream()
                        .filter(s -> ownedSet.contains(s.homeId()))
                        .toList();
            }
        }

        if (userStates.isEmpty()) {
            return "Toplam Kayıtlı Ev Sayısı: " + homeIds.size() + " (Canlı durum bekleniyor)";
        }

        for (HomeLiveState state : userStates) {
            BigDecimal kwPower = state.currentPowerWatts().divide(new BigDecimal("1000"), 2, RoundingMode.HALF_UP);
            sb.append("Ev Adı: ").append(state.homeName())
                    .append(" (ID: ").append(state.homeId()).append(")\n")
                    .append("  - Anlık Toplam Güç: ").append(kwPower.toPlainString()).append(" kW (").append(state.currentPowerWatts().toPlainString()).append(" Watt)\n")
                    .append("  - Bu Dönem Maliyet: ₺").append(state.currentCost().toPlainString()).append("\n")
                    .append("  - Birikmiş Enerji: ").append(state.accumulatedEnergyKwh().toPlainString()).append(" kWh\n")
                    .append("  - Aylık Bütçe: ₺").append(state.monthlyBudget().toPlainString()).append("\n")
                    .append("  - Bütçe Kullanım Oranı: %").append(state.budgetUsagePercent().toPlainString()).append("\n")
                    .append("  - Aktif Tarife Durumu: ").append(state.tariffState()).append("\n");

            if (state.appliances() != null && !state.appliances().isEmpty()) {
                sb.append("  - Bağlı Cihazlar:\n");
                for (ApplianceLiveState app : state.appliances().values()) {
                    sb.append("    * ").append(app.name()).append(" (").append(app.type()).append("): ")
                            .append("Güç: ").append(app.currentPowerWatts().toPlainString()).append(" W, ")
                            .append("Durum: ").append(app.operatingState()).append(", ")
                            .append("Sağlık: ").append(app.healthStatus());
                    if (app.healthStatus() == ApplianceHealthStatus.ANOMALOUS) {
                        sb.append(" [ANOMALİ! Güvenli Sınır: ").append(app.safePowerLimitWatts().toPlainString()).append(" W]");
                    }
                    sb.append("\n");
                }
            } else {
                sb.append("  - Cihazlar: Tanımlı cihaz yok.\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
