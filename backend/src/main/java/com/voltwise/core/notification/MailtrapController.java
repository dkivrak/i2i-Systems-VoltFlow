package com.voltwise.core.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

@RestController
public class MailtrapController {
    private static final Logger log = LoggerFactory.getLogger(MailtrapController.class);
    private final RestClient restClient;
    private final String accountId;
    private final String inboxId;
    private final String apiToken;

    public MailtrapController(
            @Value("${voltwise.mailtrap.account-id:2796288}") String accountId,
            @Value("${voltwise.mailtrap.inbox-id:4813707}") String inboxId,
            @Value("${voltwise.mailtrap.api-token:abde7b41726d706c55734888ca165fb4}") String apiToken) {
        this.accountId = accountId;
        this.inboxId = inboxId;
        this.apiToken = apiToken;
        this.restClient = RestClient.builder()
                .baseUrl("https://mailtrap.io")
                .build();
    }

    @GetMapping({"/api/notifications/inbox", "/api/v1/notifications/inbox", "/api/v1/mailtrap/messages"})
    public String getMessages() {
        try {
            String response = restClient.get()
                    .uri("/api/accounts/{accountId}/inboxes/{inboxId}/messages", accountId, inboxId)
                    .header("Api-Token", apiToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            return response != null ? response : "[]";
        } catch (Exception ex) {
            log.warn("Failed to fetch Mailtrap messages via backend proxy: {}", ex.getMessage());
            return "[]";
        }
    }

    @GetMapping({"/api/notifications/inbox/{messageId}/body", "/api/v1/notifications/inbox/{messageId}/body", "/api/v1/mailtrap/messages/{messageId}/body"})
    public String getMessageBody(@PathVariable Long messageId) {
        try {
            String response = restClient.get()
                    .uri("/api/accounts/{accountId}/inboxes/{inboxId}/messages/{messageId}/body.txt", accountId, inboxId, messageId)
                    .header("Api-Token", apiToken)
                    .retrieve()
                    .body(String.class);
            return response != null ? response : "E-posta içeriği boş.";
        } catch (Exception ex) {
            log.warn("Failed to fetch Mailtrap message body for id={}: {}", messageId, ex.getMessage());
            return "E-posta içeriği alınamadı.";
        }
    }
}
