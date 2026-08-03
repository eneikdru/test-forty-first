package com.eneik.generated.service;

import com.eneik.generated.model.Document;
import com.eneik.generated.model.DocumentVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final List<NotificationRecord> dispatchedNotifications = Collections.synchronizedList(new ArrayList<>());

    public void dispatchDocumentUpdateNotification(Document document, DocumentVersion version) {
        String message = String.format("Document updated: '%s' (ID: %d), New Version: %d, Path: %s",
                document.getTitle(), document.getId(), version.getVersionNumber(), version.getFilePath());

        log.info("[Telegram/Max Notification Dispatch] Sending notification... Message: {}", message);

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
