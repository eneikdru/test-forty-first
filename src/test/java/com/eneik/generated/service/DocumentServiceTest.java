package com.eneik.generated.service;

import com.eneik.generated.model.Document;
import com.eneik.generated.model.DocumentVersion;
import com.eneik.generated.repository.DocumentRepository;
import com.eneik.generated.repository.DocumentVersionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class DocumentServiceTest {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Test
    public void testSaveNewDocument() {
        String title = "Reglament FBUN";
        String filePath = "/files/reglament_fbun.pdf";
        String metadata = "{\"author\": \"Admin\", \"department\": \"Epidemiology\"}";

        Document savedDoc = documentService.saveNewDocument(title, filePath, metadata);

        assertNotNull(savedDoc.getId());
        assertEquals(title, savedDoc.getTitle());
        assertEquals(filePath, savedDoc.getFilePath());
        assertEquals(metadata, savedDoc.getMetadata());

        List<DocumentVersion> versions = documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(savedDoc.getId());
        assertEquals(1, versions.size());

        DocumentVersion version1 = versions.get(0);
        assertEquals(1, version1.getVersionNumber());
        assertEquals(filePath, version1.getFilePath());
        assertEquals(metadata, version1.getMetadata());
        assertFalse(version1.getArchived());
    }

    @Test
    public void testUpdateDocumentCreatesNewVersionAndArchivesOld() {
        // 1. Save new document
        String title = "FGOS Ordinatura";
        String filePath = "/files/fgos_v1.pdf";
        String metadata = "{\"version\": 1}";

        Document doc = documentService.saveNewDocument(title, filePath, metadata);
        Long docId = doc.getId();

        // Check first version
        List<DocumentVersion> versionsBefore = documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(docId);
        assertEquals(1, versionsBefore.size());
        assertFalse(versionsBefore.get(0).getArchived());

        // 2. Update document
        String newFilePath = "/files/fgos_v2.pdf";
        String newMetadata = "{\"version\": 2, \"updatedBy\": \"Content Manager\"}";

        Document updatedDoc = documentService.updateDocument(docId, title, newFilePath, newMetadata);

        assertEquals(newFilePath, updatedDoc.getFilePath());
        assertEquals(newMetadata, updatedDoc.getMetadata());

        // Check versions after update
        List<DocumentVersion> versionsAfter = documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(docId);
        assertEquals(2, versionsAfter.size());

        // New version should be version 2, active
        DocumentVersion version2 = versionsAfter.get(0);
        assertEquals(2, version2.getVersionNumber());
        assertEquals(newFilePath, version2.getFilePath());
        assertEquals(newMetadata, version2.getMetadata());
        assertFalse(version2.getArchived());

        // Old version should be version 1, archived
        DocumentVersion version1 = versionsAfter.get(1);
        assertEquals(1, version1.getVersionNumber());
        assertEquals(filePath, version1.getFilePath());
        assertEquals(metadata, version1.getMetadata());
        assertTrue(version1.getArchived());
    }
}
