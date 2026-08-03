package com.eneik.generated.service;

import com.eneik.generated.model.Document;
import com.eneik.generated.model.DocumentVersion;
import com.eneik.generated.repository.DocumentRepository;
import com.eneik.generated.repository.DocumentVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;

    public DocumentService(DocumentRepository documentRepository, DocumentVersionRepository documentVersionRepository) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
    }

    /**
     * Given a new document, When saving it, Then metadata and file path are stored.
     * Generates V1 version of the document.
     */
    @Transactional
    public Document saveNewDocument(String title, String filePath, String metadata) {
        Document document = new Document(title, filePath, metadata);
        document = documentRepository.save(document);

        DocumentVersion version = new DocumentVersion(document, 1, filePath, metadata, false);
        documentVersionRepository.save(version);

        return document;
    }

    /**
     * Given a document update, When updating, Then a new version is created and the old is archived.
     * We atomically guard the transition by archiving the active versions of the specific document ID,
     * then adding the new version.
     */
    @Transactional
    public Document updateDocument(Long documentId, String title, String newFilePath, String newMetadata) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document with id " + documentId + " not found"));

        // Determine current max version before archiving to increment properly
        List<DocumentVersion> existingVersions = documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId);
        int nextVersionNumber = 1;
        if (!existingVersions.isEmpty()) {
            nextVersionNumber = existingVersions.get(0).getVersionNumber() + 1;
        }

        // Atomically archive any currently active/non-archived versions
        documentVersionRepository.archiveActiveVersions(documentId);

        // Update document's reference file path/metadata if provided
        if (title != null) {
            document.setTitle(title);
        }
        if (newFilePath != null) {
            document.setFilePath(newFilePath);
        }
        if (newMetadata != null) {
            document.setMetadata(newMetadata);
        }
        document = documentRepository.save(document);

        // Save new version as the only active one
        DocumentVersion newVersion = new DocumentVersion(document, nextVersionNumber, document.getFilePath(), document.getMetadata(), false);
        documentVersionRepository.save(newVersion);

        return document;
    }
}
