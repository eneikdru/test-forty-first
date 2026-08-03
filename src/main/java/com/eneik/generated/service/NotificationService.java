package com.eneik.generated.service;

import com.eneik.generated.model.Document;
import com.eneik.generated.model.DocumentVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Value("${telegram.api.url:}")
    private String telegramApiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    private final List<NotificationRecord> dispatchedNotifications = Collections.synchronizedList(new ArrayList<>());

    public void dispatchDocumentUpdateNotification(Document document, DocumentVersion version) {
        String message = String.format("Document updated: '%s' (ID: %d), New Version: %d, Path: %s",
                document.getTitle(), document.getId(), version.getVersionNumber(), version.getFilePath());

        log.info("[Telegram/Max Notification Dispatch] Sending notification... Message: {}", message);

        if (telegramApiUrl != null && !telegramApiUrl.trim().isEmpty()) {
            log.info("[Telegram Notification Dispatch] Dispatched to remote Telegram API: {}", telegramApiUrl);
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("chat_id", "@educational_center_channel");
                payload.put("text", message);
                restTemplate.postForObject(telegramApiUrl + "/bot/sendMessage", payload, String.class);
                log.info("[Telegram Notification Dispatch] Successfully called remote Telegram API.");
            } catch (Exception e) {
                log.error("[Telegram Notification Dispatch] Failed to dispatch to remote Telegram API", e);
                throw new RuntimeException("Failed to dispatch to remote Telegram API", e);
            }
        }

        // Record the notification dispatch to Telegram
        NotificationRecord recordTelegram = new NotificationRecord(
                document.getId(),
                document.getTitle(),
                version.getVersionNumber(),
                version.getFilePath(),
                "Telegram",
                message
        );
        dispatchedNotifications.add(recordTelegram);

        // Record the notification dispatch to Max
        NotificationRecord recordMax = new NotificationRecord(
                document.getId(),
                document.getTitle(),
                version.getVersionNumber(),
                version.getFilePath(),
                "Max",
                message
        );
        dispatchedNotifications.add(recordMax);
    }

    public List<NotificationRecord> getDispatchedNotifications() {
        return new ArrayList<>(dispatchedNotifications);
    }

    public void clearNotifications() {
        dispatchedNotifications.clear();
    }

    public static class NotificationRecord {
        private final Long documentId;
        private final String title;
        private final Integer versionNumber;
        private final String filePath;
        private final String destination;
        private final String message;

        public NotificationRecord(Long documentId, String title, Integer versionNumber, String filePath, String destination, String message) {
            this.documentId = documentId;
            this.title = title;
            this.versionNumber = versionNumber;
            this.filePath = filePath;
            this.destination = destination;
            this.message = message;
        }

        public Long getDocumentId() {
            return documentId;
        }

        public String getTitle() {
            return title;
        }

        public Integer getVersionNumber() {
            return versionNumber;
        }

        public String getFilePath() {
            return filePath;
        }

        public String getDestination() {
            return destination;
        }

        public String getMessage() {
            return message;
        }
    }
}
