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
        String liveContext = "";
        try {
            liveContext = buildUserLiveContext(email);
        } catch (Exception ex) {
            log.warn("Failed to build user live context for chat: {}", ex.getMessage());
            liveContext = "Kullanıcının canlı ev verileri okunamadı.";
        }

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
            return generateSmartFallback(userMsg, email, liveContext);

        } catch (Exception ex) {
            log.warn("Gemini chat API error (type={}): {}", ex.getClass().getSimpleName(), ex.getMessage());
            return generateSmartFallback(userMsg, email, liveContext);
        }
    }

    private ChatResponse generateSmartFallback(String userMsg, String email, String liveContext) {
        if (!StringUtils.hasText(liveContext) || liveContext.contains("bulunmuyor")) {
            return new ChatResponse("Merhaba! VoltFlow hesabınızda henüz canlı ev bulunmuyor. Yeni bir ev ekleyerek anlık güç, bütçe ve cihaz durumlarınızı takip edebilirsiniz.");
        }

        String lower = userMsg.toLowerCase(java.util.Locale.ROOT);
        String prefix = "VoltFlow AI Canlı Ev Analizi:\n\n";
        if (lower.contains("maliyet") || lower.contains("tl") || lower.contains("bütçe") || lower.contains("fatura") || lower.contains("para") || lower.contains("çanakkale")) {
            prefix = "Canlı maliyet ve bütçe verileriniz aşağıdadır:\n\n";
        } else if (lower.contains("cihaz") || lower.contains("sağlık") || lower.contains("anomali") || lower.contains("durum")) {
            prefix = "Canlı cihaz ve sağlık durumlarınız aşağıdadır:\n\n";
        } else if (lower.contains("güç") || lower.contains("kw") || lower.contains("watt") || lower.contains("tüketim")) {
            prefix = "Canlı güç ve tüketim verileriniz aşağıdadır:\n\n";
        }

        return new ChatResponse(cleanMarkdown(prefix + liveContext.strip()));
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
                .filter(s -> s != null && s.homeId() != null && ownedSet.contains(s.homeId()))
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
                        .filter(s -> s != null && s.homeId() != null && ownedSet.contains(s.homeId()))
                        .toList();
            }
        }

        if (userStates.isEmpty()) {
            return "Toplam Kayıtlı Ev Sayısı: " + homeIds.size() + " (Canlı durum bekleniyor)";
        }

        for (HomeLiveState state : userStates) {
            if (state == null) continue;
            BigDecimal watts = state.currentPowerWatts() != null ? state.currentPowerWatts() : BigDecimal.ZERO;
            BigDecimal cost = state.currentCost() != null ? state.currentCost() : BigDecimal.ZERO;
            BigDecimal energy = state.accumulatedEnergyKwh() != null ? state.accumulatedEnergyKwh() : BigDecimal.ZERO;
            BigDecimal budget = state.monthlyBudget() != null ? state.monthlyBudget() : BigDecimal.ZERO;
            BigDecimal budgetPercent = state.budgetUsagePercent() != null ? state.budgetUsagePercent() : BigDecimal.ZERO;
            String tariff = state.tariffState() != null ? state.tariffState().name() : "NORMAL";

            BigDecimal kwPower = watts.divide(new BigDecimal("1000"), 2, RoundingMode.HALF_UP);
            sb.append("Ev Adı: ").append(state.homeName() != null ? state.homeName() : "Ev")
                    .append(" (ID: ").append(state.homeId()).append(")\n")
                    .append("  - Anlık Toplam Güç: ").append(kwPower.toPlainString()).append(" kW (").append(watts.toPlainString()).append(" Watt)\n")
                    .append("  - Bu Dönem Maliyet: ₺").append(cost.toPlainString()).append("\n")
                    .append("  - Birikmiş Enerji: ").append(energy.toPlainString()).append(" kWh\n")
                    .append("  - Aylık Bütçe: ₺").append(budget.toPlainString()).append("\n")
                    .append("  - Bütçe Kullanım Oranı: %").append(budgetPercent.toPlainString()).append("\n")
                    .append("  - Aktif Tarife Durumu: ").append(tariff).append("\n");

            if (state.appliances() != null && !state.appliances().isEmpty()) {
                sb.append("  - Bağlı Cihazlar:\n");
                for (ApplianceLiveState app : state.appliances().values()) {
                    if (app == null) continue;
                    BigDecimal appWatts = app.currentPowerWatts() != null ? app.currentPowerWatts() : BigDecimal.ZERO;
                    BigDecimal safeWatts = app.safePowerLimitWatts() != null ? app.safePowerLimitWatts() : BigDecimal.ZERO;
                    sb.append("    * ").append(app.name() != null ? app.name() : "Cihaz")
                            .append(" (").append(app.type() != null ? app.type() : "CİHAZ").append("): ")
                            .append("Güç: ").append(appWatts.toPlainString()).append(" W, ")
                            .append("Durum: ").append(app.operatingState() != null ? app.operatingState() : "STANDBY").append(", ")
                            .append("Sağlık: ").append(app.healthStatus() != null ? app.healthStatus() : "NORMAL");
                    if (app.healthStatus() == ApplianceHealthStatus.ANOMALOUS) {
                        sb.append(" [ANOMALİ! Güvenli Sınır: ").append(safeWatts.toPlainString()).append(" W]");
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
