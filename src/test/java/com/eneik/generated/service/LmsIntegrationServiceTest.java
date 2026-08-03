package com.eneik.generated.service;

import com.eneik.generated.model.*;
import com.eneik.generated.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class LmsIntegrationServiceTest {

    @Autowired
    private LmsIntegrationService lmsIntegrationService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private LmsLinkRepository lmsLinkRepository;

    @Autowired
    private UserAnalyticsRepository userAnalyticsRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testIndexExternalLmsDocumentMapsToUnifiedSearchSchema() throws Exception {
        Map<String, Object> remoteMetadata = new HashMap<>();
        remoteMetadata.put("subject", "Epidemiology");
        remoteMetadata.put("course", "Infectious Diseases");

        ExternalLmsDocument extDoc = new ExternalLmsDocument(
                "LMS-FBUN-01",
                "ext-doc-12345",
                "Metodicheskie rekomendatsii po grippu",
                "/remote/files/flu_guide.pdf",
                "https://lms.fbun.ru/courses/10/files/flu_guide.pdf",
                remoteMetadata
        );

        Document indexedDoc = lmsIntegrationService.indexExternalLmsDocument(extDoc);

        assertNotNull(indexedDoc.getId());
        assertEquals("Metodicheskie rekomendatsii po grippu", indexedDoc.getTitle());
        assertEquals("/remote/files/flu_guide.pdf", indexedDoc.getFilePath());

        // Parse and verify indexed document's metadata (Unified Search Schema)
        String docMetadataStr = indexedDoc.getMetadata();
        assertNotNull(docMetadataStr);
        Map<String, Object> docMetadata = objectMapper.readValue(docMetadataStr, Map.class);
        assertEquals("Epidemiology", docMetadata.get("subject"));
        assertEquals("Infectious Diseases", docMetadata.get("course"));
        assertEquals("LMS-FBUN-01", docMetadata.get("externalSystemId"));
        assertEquals("ext-doc-12345", docMetadata.get("externalDocId"));
        assertEquals("https://lms.fbun.ru/courses/10/files/flu_guide.pdf", docMetadata.get("lmsUrl"));

        // Verify that lms_links relation is correctly stored
        List<LmsLink> links = lmsLinkRepository.findByDocumentId(indexedDoc.getId());
        assertEquals(1, links.size());
        LmsLink link = links.get(0);
        assertEquals("LMS-FBUN-01", link.getExternalSystemId());
        assertEquals("ext-doc-12345", link.getExternalDocId());
        assertEquals("https://lms.fbun.ru/courses/10/files/flu_guide.pdf", link.getLmsUrl());

        Map<String, Object> linkRemoteMetadata = objectMapper.readValue(link.getRemoteMetadata(), Map.class);
        assertEquals("Epidemiology", linkRemoteMetadata.get("subject"));
        assertEquals("Infectious Diseases", linkRemoteMetadata.get("course"));
    }

    @Test
    public void testRecordUserAnalyticsSupportsEiosExportFormats() throws Exception {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("ip", "192.168.1.100");
        metadata.put("device", "Desktop");

        LocalDateTime startRange = LocalDateTime.now().minusMinutes(5);

        UserAnalytics recorded = lmsIntegrationService.recordUserAnalytics(
                "student_08",
                "VIEW_DOCUMENT",
                "doc_999",
                "DOCUMENT",
                metadata
        );

        assertNotNull(recorded.getId());
        assertEquals("student_08", recorded.getUserId());
        assertEquals("VIEW_DOCUMENT", recorded.getActionType());
        assertEquals("doc_999", recorded.getResourceId());
        assertEquals("DOCUMENT", recorded.getResourceType());
        assertNotNull(recorded.getTimestamp());

        Map<String, Object> recMetadata = objectMapper.readValue(recorded.getMetadata(), Map.class);
        assertEquals("192.168.1.100", recMetadata.get("ip"));
        assertEquals("Desktop", recMetadata.get("device"));

        LocalDateTime endRange = LocalDateTime.now().plusMinutes(5);

        // Export to EIOS format
        List<EiosExportRecord> exportRecords = lmsIntegrationService.exportToEiosFormat(startRange, endRange);
        assertFalse(exportRecords.isEmpty());

        EiosExportRecord matchedRecord = exportRecords.stream()
                .filter(r -> r.getUserId().equals("student_08"))
                .findFirst()
                .orElse(null);

        assertNotNull(matchedRecord);
        assertEquals("VIEW_DOCUMENT", matchedRecord.getActionType());
        assertEquals("doc_999", matchedRecord.getResourceId());
        assertEquals("DOCUMENT", matchedRecord.getResourceType());
        assertNotNull(matchedRecord.getTimestamp());

        Map<String, Object> expMetadata = objectMapper.readValue(matchedRecord.getMetadata(), Map.class);
        assertEquals("192.168.1.100", expMetadata.get("ip"));
        assertEquals("Desktop", expMetadata.get("device"));
    }
}
