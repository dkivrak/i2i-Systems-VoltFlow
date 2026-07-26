package com.voltwise.core.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltwise.core.persistence.entity.NotificationEntity;
import com.voltwise.core.persistence.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class MailtrapController {
    private static final Logger log = LoggerFactory.getLogger(MailtrapController.class);
    private final RestClient restClient;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;
    private final String accountId;
    private final String inboxId;
    private final String apiToken;

    public MailtrapController(
            NotificationRepository notificationRepository,
            ObjectMapper objectMapper,
            @Value("${voltwise.mailtrap.account-id:2796288}") String accountId,
            @Value("${voltwise.mailtrap.inbox-id:4813707}") String inboxId,
            @Value("${voltwise.mailtrap.api-token:abde7b41726d706c55734888ca165fb4}") String apiToken) {
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
        this.accountId = accountId;
        this.inboxId = inboxId;
        this.apiToken = apiToken;
        this.restClient = RestClient.builder()
                .baseUrl("https://mailtrap.io")
                .build();
    }

    @Transactional(readOnly = true)
    @GetMapping({"/api/notifications/inbox", "/api/v1/notifications/inbox", "/api/v1/mailtrap/messages"})
    public List<Map<String, Object>> getMessages() {
        List<Map<String, Object>> resultList = new ArrayList<>();
        String currentEmail = com.voltwise.core.auth.UserContext.getCurrentUserEmail();

        if (currentEmail == null || currentEmail.isBlank()) {
            currentEmail = "onur@gmail.com";
        }

        try {
            var dbNotifications = notificationRepository.findByRecipientIgnoreCaseOrderByIdDesc(currentEmail);
            var limitedNotifications = dbNotifications.stream().limit(20).toList();

            if (!limitedNotifications.isEmpty()) {
                for (var n : limitedNotifications) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", n.getId());
                    map.put("subject", n.getSubject());
                    map.put("to_email", n.getRecipient() != null ? n.getRecipient() : currentEmail);
                    map.put("to_name", "VoltFlow Kullanıcısı");
                    map.put("created_at", n.getCreatedAt() != null ? n.getCreatedAt().toString() : java.time.Instant.now().toString());
                    resultList.add(map);
                }
            } else {
                // Fallback for new accounts: Map system notifications to current user email, capped at 20
                var allNotifications = notificationRepository.findAllByOrderByCreatedAtDesc().stream().limit(20).toList();
                for (var n : allNotifications) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", n.getId());
                    map.put("subject", n.getSubject());
                    map.put("to_email", currentEmail);
                    map.put("to_name", "VoltFlow Kullanıcısı");
                    map.put("created_at", n.getCreatedAt() != null ? n.getCreatedAt().toString() : java.time.Instant.now().toString());
                    resultList.add(map);
                }
            }
        } catch (Exception ex) {
            log.error("Error fetching DB notifications in MailtrapController: {}", ex.getMessage(), ex);
        }

        return resultList;
    }

    @Transactional(readOnly = true)
    @GetMapping({"/api/notifications/inbox/{messageId}/body", "/api/v1/notifications/inbox/{messageId}/body", "/api/v1/mailtrap/messages/{messageId}/body"})
    public String getMessageBody(@PathVariable Long messageId) {
        // Try DB notification first
        try {
            var opt = notificationRepository.findById(messageId);
            if (opt.isPresent()) {
                NotificationEntity n = opt.get();
                if (n.getRecommendation() != null && StringUtils.hasText(n.getRecommendation().getRecommendationText())) {
                    return cleanMarkdown(n.getRecommendation().getRecommendationText());
                }
                return "VoltFlow Güvenlik Bildirimi: " + n.getSubject() + "\n\nCihazınızda olağan dışı tüketim veya bütçe eşik aşımı tespit edilmiştir. Lütfen cihazlarınızı ve güncel tüketim değerlerinizi kontrol ediniz.";
            }
        } catch (Exception ex) {
            log.warn("DB notification read exception for messageId={}: {}", messageId, ex.getMessage());
        }

        // Fallback to Mailtrap API
        try {
            String response = restClient.get()
                    .uri("/api/accounts/{accountId}/inboxes/{inboxId}/messages/{messageId}/body.txt", accountId, inboxId, messageId)
                    .header("Api-Token", apiToken)
                    .retrieve()
                    .body(String.class);
            return (response != null && !response.isBlank()) ? cleanMarkdown(response) : "Cihazınızda olağan dışı tüketim algılanmıştır. Lütfen cihazlarınızı kontrol ediniz.";
        } catch (Exception ex) {
            log.warn("Failed to fetch Mailtrap message body for id={}: {}", messageId, ex.getMessage());
            return "Cihazınızda olağan dışı tüketim veya bütçe eşik aşımı tespit edilmiştir. Lütfen VoltFlow paneli üzerinden güncel tüketim değerlerinizi kontrol ediniz.";
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
}
