package com.eneik.generated;

import com.eneik.generated.model.Document;
import com.eneik.generated.model.DocumentVersion;
import com.eneik.generated.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class DocumentServiceTest {

    @Autowired
    private DocumentService documentService;

    @Test
    public void testSaveNewDocument_StoresMetadataAndFilePath() {
        // Given
        String title = "FGBU Epidemology Guideline";
        String description = "Standard procedure rules for 2026";
        String filePath = "/files/guideline_v1.pdf";

        // When
        Document savedDoc = documentService.saveNewDocument(title, description, filePath);

        // Then
        assertThat(savedDoc.getId()).isNotNull();
        assertThat(savedDoc.getTitle()).isEqualTo(title);
        assertThat(savedDoc.getDescription()).isEqualTo(description);
        assertThat(savedDoc.getFilePath()).isEqualTo(filePath);
        assertThat(savedDoc.getCreatedAt()).isNotNull();
        assertThat(savedDoc.getUpdatedAt()).isNotNull();

        // Verify initial version 1 was also automatically created and is active
        List<DocumentVersion> versions = documentService.getDocumentVersions(savedDoc.getId());
        assertThat(versions).hasSize(1);
        DocumentVersion v1 = versions.get(0);
        assertThat(v1.getVersionNumber()).isEqualTo(1);
        assertThat(v1.getTitle()).isEqualTo(title);
        assertThat(v1.getDescription()).isEqualTo(description);
        assertThat(v1.getFilePath()).isEqualTo(filePath);
        assertThat(v1.getArchived()).isFalse();
    }

    @Test
    public void testUpdateDocument_CreatesNewVersionAndArchivesOld() {
        // Given
        Document initialDoc = documentService.saveNewDocument(
                "Initial Title",
                "Initial Description",
                "/files/initial.pdf"
        );
        Long docId = initialDoc.getId();

        // When
        String updatedTitle = "Updated Title";
        String updatedDescription = "Updated Description";
        String updatedFilePath = "/files/updated.pdf";

        Document updatedDoc = documentService.updateDocument(docId, updatedTitle, updatedDescription, updatedFilePath);

        // Then
        assertThat(updatedDoc.getTitle()).isEqualTo(updatedTitle);
        assertThat(updatedDoc.getDescription()).isEqualTo(updatedDescription);
        assertThat(updatedDoc.getFilePath()).isEqualTo(updatedFilePath);

        // Fetch versions and verify
        List<DocumentVersion> versions = documentService.getDocumentVersions(docId);
        // Ordered by version number desc, so index 0 should be version 2 and index 1 should be version 1
        assertThat(versions).hasSize(2);

        DocumentVersion latestVersion = versions.get(0);
        assertThat(latestVersion.getVersionNumber()).isEqualTo(2);
        assertThat(latestVersion.getTitle()).isEqualTo(updatedTitle);
        assertThat(latestVersion.getDescription()).isEqualTo(updatedDescription);
        assertThat(latestVersion.getFilePath()).isEqualTo(updatedFilePath);
        assertThat(latestVersion.getArchived()).isFalse();

        DocumentVersion oldVersion = versions.get(1);
        assertThat(oldVersion.getVersionNumber()).isEqualTo(1);
        assertThat(oldVersion.getTitle()).isEqualTo("Initial Title");
        assertThat(oldVersion.getDescription()).isEqualTo("Initial Description");
        assertThat(oldVersion.getFilePath()).isEqualTo("/files/initial.pdf");
        assertThat(oldVersion.getArchived()).isTrue(); // The old version is archived!
    }
}
