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

        // Build live context for the user's homes (filtering by specifically mentioned home if any)
        String liveContext = "";
        try {
            liveContext = buildUserLiveContext(email, userMsg);
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
                - EĞER KULLANICI BELİRLİ BİR EV VEYA CİHAZ SORDUYSA (örneğin "Çanakkale" veya "Buzdolabı"), SADECE O EVE VEYA CİHAZA AİT BİLGİLERİ AÇIKLA. Diğer evleri yanıtına dahil etme!
                - Kullanıcının "Bu dönem maliyet", "Anlık toplam güç", "Sağlıklı evler", "Cihaz durumu" gibi sorularına yukarıdaki GERÇEK CANLI VERİLERİ kullanarak 2-4 cümlelik net ve yapıcı cevaplar ver.
                - Rakamları panelde göründüğü gibi (örneğin "₺0,15" veya "2,77 kW") tam doğrulukla ifade et.
                - Enerji tasarrufu ipuçları ver ve samimi bir üslup kullan.
                """.formatted(email, liveContext);

        String model = StringUtils.hasText(properties.getGemini().getModel())
                ? properties.getGemini().getModel()
                : "gemini-flash-lite-latest";
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

            String targetUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
            JsonNode response = RestClient.create().post()
                    .uri(java.net.URI.create(targetUrl))
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
            return new ChatResponse("Merhaba! VoltFlow hesabınızda henüz canlı ev bulunmuyor. Yeni bir ev ekleyerek anlık güç, bütçe ve cihaz durumlarınızı buradan takip edebilirsiniz.");
        }

        List<Long> homeIds = homeRepository.findIdsByOwnerEmail(email);
        Set<Long> ownedSet = Set.copyOf(homeIds);
        List<HomeLiveState> userStates = liveStateStore.getAll().stream()
                .filter(s -> s != null && s.homeId() != null && ownedSet.contains(s.homeId()))
                .toList();

        // Filter userStates if userMsg mentions a specific home name
        String lowerMsg = userMsg != null ? userMsg.toLowerCase(java.util.Locale.ROOT) : "";
        List<HomeLiveState> targetStates = userStates;
        if (StringUtils.hasText(lowerMsg)) {
            List<HomeLiveState> matched = userStates.stream()
                    .filter(s -> {
                        if (s == null) return false;
                        if (s.homeName() != null) {
                            String nameLower = s.homeName().toLowerCase(java.util.Locale.ROOT);
                            if (lowerMsg.contains(nameLower)) return true;
                            String[] words = nameLower.split("\\s+");
                            for (String w : words) {
                                if (w.length() >= 3 && !w.equals("ev") && lowerMsg.contains(w)) {
                                    return true;
                                }
                            }
                        }
                        return false;
                    })
                    .toList();
            if (!matched.isEmpty()) {
                targetStates = matched;
            }
        }

        if (targetStates == null || targetStates.isEmpty()) {
            targetStates = userStates;
        }

        // Calculate Grand Totals across target states
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalWatts = BigDecimal.ZERO;
        BigDecimal totalBudget = BigDecimal.ZERO;
        for (HomeLiveState s : targetStates) {
            if (s == null) continue;
            if (s.currentCost() != null) totalCost = totalCost.add(s.currentCost());
            if (s.currentPowerWatts() != null) totalWatts = totalWatts.add(s.currentPowerWatts());
            if (s.monthlyBudget() != null) totalBudget = totalBudget.add(s.monthlyBudget());
        }
        BigDecimal totalKw = totalWatts.divide(new BigDecimal("1000"), 2, RoundingMode.HALF_UP);

        // 1. Selamlaşma veya Genel Yetenek Soruları
        if (lowerMsg.contains("merhaba") || lowerMsg.contains("selam") || lowerMsg.contains("kimsin") || lowerMsg.contains("yardım") || lowerMsg.contains("ne yapabilir")) {
            return new ChatResponse("Merhaba! Ben VoltFlow AI canlı enerji danışmanınız. Evlerinizin anlık güç tüketimini, maliyet ve bütçe durumunu, cihaz sağlıklarını takip edebilir ve tasarruf tavsiyeleri verebilirim. Nasıl yardımcı olabilirim?");
        }

        // 2. Ev Sayısı veya Ev Listesi Soruları (Kaç evim var, hangi evler var vb.)
        if ((lowerMsg.contains("kaç") && (lowerMsg.contains("ev") || lowerMsg.contains("mülk") || lowerMsg.contains("konut"))) || lowerMsg.contains("ev sayısı") || lowerMsg.contains("hangi evler var") || lowerMsg.contains("evlerimin listesi")) {
            StringBuilder countReply = new StringBuilder();
            countReply.append("VoltFlow hesabınıza tanımlı toplam ").append(targetStates.size()).append(" adet canlı ev bulunmaktadır:\n\n");
            for (int i = 0; i < targetStates.size(); i++) {
                HomeLiveState s = targetStates.get(i);
                if (s != null) {
                    countReply.append(i + 1).append(". 🏠 ").append(s.homeName()).append("\n");
                }
            }
            return new ChatResponse(cleanMarkdown(countReply.toString().strip()));
        }

        // 3. Cihaz Sayısı Soruları (Kaç cihazım var vb.)
        if (lowerMsg.contains("kaç") && (lowerMsg.contains("cihaz") || lowerMsg.contains("alet"))) {
            int totalApp = 0;
            for (HomeLiveState s : targetStates) {
                if (s != null && s.appliances() != null) totalApp += s.appliances().size();
            }
            return new ChatResponse("Kayıtlı evlerinizde bağlı ve anlık izlenen toplam " + totalApp + " adet akıllı cihaz bulunmaktadır.");
        }

        // 4. En çok yakan cihaz / En yüksek tüketim soruları
        if (lowerMsg.contains("en çok") || lowerMsg.contains("en fazla") || lowerMsg.contains("en yüksek") || lowerMsg.contains("hangi cihaz")) {
            ApplianceLiveState maxApp = null;
            HomeLiveState maxHome = null;
            BigDecimal maxWatts = BigDecimal.ZERO;

            for (HomeLiveState s : targetStates) {
                if (s != null && s.appliances() != null) {
                    for (ApplianceLiveState app : s.appliances().values()) {
                        if (app != null && app.currentPowerWatts() != null) {
                            if (app.currentPowerWatts().compareTo(maxWatts) > 0) {
                                maxWatts = app.currentPowerWatts();
                                maxApp = app;
                                maxHome = s;
                            }
                        }
                    }
                }
            }

            if (maxApp != null && maxHome != null && maxWatts.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal kw = maxWatts.divide(new BigDecimal("1000"), 2, RoundingMode.HALF_UP);
                return new ChatResponse(cleanMarkdown(
                    "Şu anda en yüksek güç tüketen cihazınız " + maxHome.homeName() + " evinizdeki " +
                    maxApp.name() + " cihazıdır. Anlık olarak " + kw.toPlainString() + " kW (" +
                    maxWatts.setScale(0, RoundingMode.HALF_UP).toPlainString() + " Watt) güç çekmektedir."
                ));
            }
        }

        // 2. Özel "Toplam" Sorusu (Toplam maliyet, toplam güç vb.)
        if (lowerMsg.contains("toplam") || lowerMsg.contains("hepsi") || lowerMsg.contains("genel toplam")) {
            StringBuilder sb = new StringBuilder();
            sb.append("📊 Kayıtlı evlerinizin GENEL TOPLAM durumu:\n\n");
            sb.append("• 💰 Toplam Birikmiş Tüketim Maliyeti: ₺").append(formatMoneyTurkish(totalCost)).append("\n");
            sb.append("• ⚡ Toplam Anlık Güç Tüketimi: ").append(formatPercentTurkish(totalKw)).append(" kW (").append(totalWatts.setScale(0, RoundingMode.HALF_UP).toPlainString()).append(" Watt)\n");
            if (totalBudget.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal totalPercent = totalCost.multiply(new BigDecimal("100")).divide(totalBudget, 2, RoundingMode.HALF_UP);
                sb.append("• 📈 Toplam Bütçe Kullanımı: %").append(formatPercentTurkish(totalPercent)).append(" (₺").append(formatMoneyTurkish(totalBudget)).append(" bütçeden)\n");
            }
            sb.append("\nEv bazlı detaylı dağılım:\n");
            for (HomeLiveState s : targetStates) {
                if (s == null) continue;
                BigDecimal cost = s.currentCost() != null ? s.currentCost() : BigDecimal.ZERO;
                BigDecimal watts = s.currentPowerWatts() != null ? s.currentPowerWatts() : BigDecimal.ZERO;
                BigDecimal kw = watts.divide(new BigDecimal("1000"), 2, RoundingMode.HALF_UP);
                sb.append("• 🏠 ").append(s.homeName()).append(": ₺").append(formatMoneyTurkish(cost))
                  .append(" maliyet | Anlık ").append(formatPercentTurkish(kw)).append(" kW\n");
            }
            return new ChatResponse(cleanMarkdown(sb.toString().strip()));
        }

        // 3. Belirli bir Cihaz Sorusu (Buzdolabı, Çamaşır Makinesi, Klima, Kettle vb.)
        for (HomeLiveState state : targetStates) {
            if (state != null && state.appliances() != null) {
                for (ApplianceLiveState app : state.appliances().values()) {
                    if (app == null || app.name() == null) continue;
                    String appNameLower = app.name().toLowerCase(java.util.Locale.ROOT);
                    String typeLower = app.type() != null ? app.type().name().toLowerCase(java.util.Locale.ROOT) : "";
                    if (lowerMsg.contains(appNameLower) || (StringUtils.hasText(typeLower) && lowerMsg.contains(typeLower))) {
                        BigDecimal watts = app.currentPowerWatts() != null ? app.currentPowerWatts() : BigDecimal.ZERO;
                        BigDecimal safeWatts = app.safePowerLimitWatts() != null ? app.safePowerLimitWatts() : BigDecimal.ZERO;
                        String statusStr = app.healthStatus() == ApplianceHealthStatus.ANOMALOUS
                                ? "⚠️ ANOMALİ! Güvenli sınır (" + safeWatts.toPlainString() + " W) aşıldı."
                                : "🟢 Normal ve sağlıklı.";

                        return new ChatResponse(cleanMarkdown(
                            cleanHomeName(state.homeName()) + " " + app.name() + " cihazının anlık güç tüketimi " +
                            watts.setScale(0, RoundingMode.HALF_UP).toPlainString() + " Watt seviyesindedir. Çalışma durumu " + app.operatingState() +
                            ", sağlık durumu ise " + statusStr
                        ));
                    }
                }
            }
        }

        // 4. Tasarruf ve Öneri Sorusu
        if (lowerMsg.contains("tasarruf") || lowerMsg.contains("tavsiye") || lowerMsg.contains("ipucu") || lowerMsg.contains("nasıl düşür") || lowerMsg.contains("öneri")) {
            StringBuilder advice = new StringBuilder();
            HomeLiveState first = targetStates.get(0);
            advice.append(cleanHomeName(first.homeName())).append(" enerji maliyetlerini düşürmek için öneriler:\n\n");
            advice.append("• Yüksek güç çeken cihazları (Kettle, Çamaşır Makinesi vb.) ceza tarifesi saatlerinde kullanmaktan kaçının.\n");
            advice.append("• Bekleme (standby) modunda güç tüketen cihazların fişini çekerek %10-15 tasarruf sağlayabilirsiniz.\n");
            advice.append("• Güncel bütçe kullanım oranınız %").append(formatPercentTurkish(first.budgetUsagePercent())).append(" seviyesindedir.");
            return new ChatResponse(cleanMarkdown(advice.toString()));
        }

        // 5. Maliyet, Fatura, TL veya Bütçe Sorusu
        if (lowerMsg.contains("maliyet") || lowerMsg.contains("tl") || lowerMsg.contains("bütçe") || lowerMsg.contains("fatura") || lowerMsg.contains("para") || lowerMsg.contains("borç") || lowerMsg.contains("ne kadar")) {
            StringBuilder costReply = new StringBuilder();
            if (targetStates.size() > 1) {
                costReply.append("💰 Tüm evlerinizin TOPLAM dönem maliyeti: ₺").append(formatMoneyTurkish(totalCost)).append("\n\n");
                costReply.append("Ev bazlı tüketim maliyetleri:\n");
            }
            for (HomeLiveState s : targetStates) {
                if (s == null) continue;
                BigDecimal cost = s.currentCost() != null ? s.currentCost() : BigDecimal.ZERO;
                BigDecimal budget = s.monthlyBudget() != null ? s.monthlyBudget() : BigDecimal.ZERO;
                BigDecimal percent = s.budgetUsagePercent() != null ? s.budgetUsagePercent() : BigDecimal.ZERO;

                String costStr = formatMoneyTurkish(cost);
                String budgetStr = formatMoneyTurkish(budget);
                String percentStr = formatPercentTurkish(percent);

                if (targetStates.size() == 1) {
                    costReply.append(cleanHomeName(s.homeName())).append(" bu dönemki birikmiş tüketim maliyeti ₺").append(costStr).append("'dir.\n");
                    costReply.append("Aylık ₺").append(budgetStr).append(" bütçenizden %").append(percentStr).append(" kullanılmıştır. ");
                    if (percent.compareTo(new BigDecimal("80")) >= 0) {
                        costReply.append("⚠️ Bütçe sınırınıza yaklaşılmaktadır.");
                    } else {
                        costReply.append("Bütçe durumunuz oldukça güvendedir.");
                    }
                } else {
                    costReply.append("• 🏠 ").append(s.homeName()).append(": ₺").append(costStr)
                             .append(" maliyet | Bütçe: ₺").append(budgetStr)
                             .append(" (%").append(percentStr).append(" kullanım)\n");
                }
            }
            return new ChatResponse(cleanMarkdown(costReply.toString().strip()));
        }

        // 6. Anlık Güç, Watt veya Tüketim Sorusu
        if (lowerMsg.contains("güç") || lowerMsg.contains("kw") || lowerMsg.contains("watt") || lowerMsg.contains("tüketim") || lowerMsg.contains("harcama")) {
            StringBuilder powerReply = new StringBuilder();
            if (targetStates.size() > 1) {
                powerReply.append("⚡ Tüm evlerinizin TOPLAM anlık gücü: ").append(formatPercentTurkish(totalKw)).append(" kW (").append(totalWatts.setScale(0, RoundingMode.HALF_UP).toPlainString()).append(" W)\n\n");
                powerReply.append("Ev bazlı anlık tüketim durumları:\n");
            }
            for (HomeLiveState s : targetStates) {
                if (s == null) continue;
                BigDecimal watts = s.currentPowerWatts() != null ? s.currentPowerWatts() : BigDecimal.ZERO;
                BigDecimal kw = watts.divide(new BigDecimal("1000"), 2, RoundingMode.HALF_UP);
                BigDecimal energy = s.accumulatedEnergyKwh() != null ? s.accumulatedEnergyKwh() : BigDecimal.ZERO;

                String kwStr = formatPercentTurkish(kw);
                String energyStr = formatPercentTurkish(energy);
                String wattsStr = watts.setScale(0, RoundingMode.HALF_UP).toPlainString();

                if (targetStates.size() == 1) {
                    powerReply.append(cleanHomeName(s.homeName())).append(" anlık toplam ").append(kwStr).append(" kW (").append(wattsStr).append(" Watt) güç çekilmektedir. ");
                    powerReply.append("Bu dönem biriken toplam enerji tüketimi ").append(energyStr).append(" kWh olarak ölçülmüştür.");
                } else {
                    powerReply.append("• 🏠 ").append(s.homeName()).append(": Anlık ").append(kwStr)
                              .append(" kW (").append(wattsStr).append(" W) | Toplam Enerji: ").append(energyStr).append(" kWh\n");
                }
            }
            return new ChatResponse(cleanMarkdown(powerReply.toString().strip()));
        }

        // 7. Genel Canlı Durum Özeti
        if (targetStates.size() == 1) {
            HomeLiveState s = targetStates.get(0);
            BigDecimal kw = (s.currentPowerWatts() != null ? s.currentPowerWatts() : BigDecimal.ZERO)
                    .divide(new BigDecimal("1000"), 2, RoundingMode.HALF_UP);
            BigDecimal cost = s.currentCost() != null ? s.currentCost() : BigDecimal.ZERO;
            BigDecimal percent = s.budgetUsagePercent() != null ? s.budgetUsagePercent() : BigDecimal.ZERO;

            return new ChatResponse(cleanMarkdown(
                cleanHomeName(s.homeName()) + " canlı durumu: Anlık çekilen güç " + formatPercentTurkish(kw) +
                " kW, bu dönemki maliyet ₺" + formatMoneyTurkish(cost) + " ve bütçe kullanım oranı %" +
                formatPercentTurkish(percent) + "'dir. Tüm cihazlarınız sağlıklı çalışmaktadır."
            ));
        } else {
            StringBuilder general = new StringBuilder();
            general.append("⚡ Tüm evlerinizin TOPLAM anlık gücü: ").append(formatPercentTurkish(totalKw)).append(" kW | TOPLAM maliyeti: ₺").append(formatMoneyTurkish(totalCost)).append("\n\n");
            general.append("Kayıtlı evlerinizin canlı özet verileri:\n");
            for (HomeLiveState s : targetStates) {
                if (s == null) continue;
                BigDecimal kw = (s.currentPowerWatts() != null ? s.currentPowerWatts() : BigDecimal.ZERO)
                        .divide(new BigDecimal("1000"), 2, RoundingMode.HALF_UP);
                BigDecimal cost = s.currentCost() != null ? s.currentCost() : BigDecimal.ZERO;
                general.append("• 🏠 ").append(s.homeName()).append(": Anlık ").append(formatPercentTurkish(kw))
                        .append(" kW | Maliyet: ₺").append(formatMoneyTurkish(cost)).append("\n");
            }
            return new ChatResponse(cleanMarkdown(general.toString()));
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

    private static String cleanHomeName(String homeName) {
        if (!StringUtils.hasText(homeName)) return "Evinizin";
        String trimmed = homeName.strip();
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(" ev") || lower.endsWith(" evi")) {
            return trimmed + "'nizin";
        }
        return trimmed + " evinizin";
    }

    private static String formatMoneyTurkish(BigDecimal val) {
        if (val == null) return "0,00";
        BigDecimal rounded = val.setScale(2, RoundingMode.HALF_UP);
        return String.format(new java.util.Locale("tr", "TR"), "%,.2f", rounded);
    }

    private static String formatPercentTurkish(BigDecimal val) {
        if (val == null) return "0,00";
        BigDecimal rounded = val.setScale(2, RoundingMode.HALF_UP);
        return String.format(new java.util.Locale("tr", "TR"), "%,.2f", rounded);
    }

    private String buildUserLiveContext(String email, String userMsg) {
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

        // Eğer kullanıcı belirli bir evin adını sorduysa (örn: "Çanakkale", "İstanbul", "Yazlık"), sadece o evi filtrele!
        String lowerMsg = StringUtils.hasText(userMsg) ? userMsg.toLowerCase(java.util.Locale.ROOT) : "";
        List<HomeLiveState> targetStates = userStates;
        if (StringUtils.hasText(lowerMsg)) {
            List<HomeLiveState> matched = userStates.stream()
                    .filter(s -> s.homeName() != null && lowerMsg.contains(s.homeName().toLowerCase(java.util.Locale.ROOT)))
                    .toList();
            if (!matched.isEmpty()) {
                targetStates = matched;
            }
        }

        for (HomeLiveState state : targetStates) {
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
