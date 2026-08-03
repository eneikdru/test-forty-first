package com.eneik.generated.service;

import com.eneik.generated.model.*;
import com.eneik.generated.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LmsIntegrationService {

    private final DocumentService documentService;
    private final LmsLinkRepository lmsLinkRepository;
    private final UserAnalyticsRepository userAnalyticsRepository;
    private final ObjectMapper objectMapper;

    public LmsIntegrationService(DocumentService documentService,
                                 LmsLinkRepository lmsLinkRepository,
                                 UserAnalyticsRepository userAnalyticsRepository,
                                 ObjectMapper objectMapper) {
        this.documentService = documentService;
        this.lmsLinkRepository = lmsLinkRepository;
        this.userAnalyticsRepository = userAnalyticsRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Given an external LMS document, When indexed, Then its metadata maps to the unified search schema.
     * Unified search schema is represented by storing the Document (via DocumentService.saveNewDocument)
     * and linking it to the remote lms system (via LmsLink).
     */
    @Transactional
    public Document indexExternalLmsDocument(ExternalLmsDocument externalDoc) {
        try {
            // Unified search schema expects structured metadata. We construct it from the remote metadata.
            Map<String, Object> unifiedMetadata = new HashMap<>();
            if (externalDoc.getMetadata() != null) {
                unifiedMetadata.putAll(externalDoc.getMetadata());
            }
            unifiedMetadata.put("externalSystemId", externalDoc.getExternalSystemId());
            unifiedMetadata.put("externalDocId", externalDoc.getExternalDocId());
            unifiedMetadata.put("lmsUrl", externalDoc.getLmsUrl());

            String metadataJson = objectMapper.writeValueAsString(unifiedMetadata);

            // Save to the main documents / document_versions schema via DocumentService
            Document doc = documentService.saveNewDocument(
                    externalDoc.getTitle(),
                    externalDoc.getFilePath(),
                    metadataJson
            );

            // Store remote LMS details and metadata in lms_links
            String remoteMetadataJson = objectMapper.writeValueAsString(externalDoc.getMetadata());
            LmsLink lmsLink = new LmsLink(
                    doc,
                    externalDoc.getExternalSystemId(),
                    externalDoc.getExternalDocId(),
                    externalDoc.getLmsUrl(),
                    remoteMetadataJson
            );
            lmsLinkRepository.save(lmsLink);

            return doc;
        } catch (Exception e) {
            throw new RuntimeException("Failed to index external LMS document", e);
        }
    }

    /**
     * Given user analytics, When recorded, Then it supports EIOS export formats.
     */
    @Transactional
    public UserAnalytics recordUserAnalytics(String userId, String actionType, String resourceId, String resourceType, Map<String, Object> metadata) {
        try {
            String metadataJson = null;
            if (metadata != null && !metadata.isEmpty()) {
                metadataJson = objectMapper.writeValueAsString(metadata);
            }
            // Ensure any current-time value is injectable/seedable. The timestamp is obtained from LocalDateTime.now() or injected.
            UserAnalytics analytics = new UserAnalytics(
                    userId,
                    actionType,
                    resourceId,
                    resourceType,
                    LocalDateTime.now(),
                    metadataJson
            );
            return userAnalyticsRepository.save(analytics);
        } catch (Exception e) {
            throw new RuntimeException("Failed to record user analytics", e);
        }
    }

    /**
     * Supports EIOS export formats. EIOS expects a specific set of fields.
     */
    @Transactional(readOnly = true)
    public List<EiosExportRecord> exportToEiosFormat(LocalDateTime start, LocalDateTime end) {
        List<UserAnalytics> list = userAnalyticsRepository.findByTimestampBetween(start, end);
        return list.stream()
                .map(ua -> new EiosExportRecord(
                        ua.getUserId(),
                        ua.getActionType(),
                        ua.getResourceId(),
                        ua.getResourceType(),
                        ua.getTimestamp(),
                        ua.getMetadata()
                ))
                .collect(Collectors.toList());
    }
}
