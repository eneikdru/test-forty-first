package com.eneik.generated.service;

import com.eneik.generated.model.Document;
import com.eneik.generated.model.DocumentVersion;
import com.eneik.generated.repository.DocumentRepository;
import com.eneik.generated.repository.DocumentVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;

    public DocumentService(DocumentRepository documentRepository, DocumentVersionRepository documentVersionRepository) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
    }

    /**
     * Saves a new document.
     * Also automatically creates its initial version (version 1) which is active (not archived).
     */
    @Transactional
    public Document saveNewDocument(String title, String description, String filePath) {
        Document document = new Document(title, description, filePath);
        // Persist document first to get the generated ID
        document = documentRepository.save(document);

        // Create initial version
        DocumentVersion initialVersion = new DocumentVersion(document, 1, title, description, filePath);
        document.addVersion(initialVersion);

        // Save version
        documentVersionRepository.save(initialVersion);

        return document;
    }

    /**
     * Updates an existing document with a new version.
     * The old version is marked as archived (is_archived = true).
     * The new version is created with version number V + 1 and is active.
     */
    @Transactional
    public Document updateDocument(Long documentId, String newTitle, String newDescription, String newFilePath) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with id: " + documentId));

        // Find the latest version of the document
        Optional<DocumentVersion> latestVersionOpt = documentVersionRepository.findFirstByDocumentOrderByVersionNumberDesc(document);
        int nextVersionNumber = 1;

        if (latestVersionOpt.isPresent()) {
            DocumentVersion latestVersion = latestVersionOpt.get();
            nextVersionNumber = latestVersion.getVersionNumber() + 1;

            // Archive the old version
            latestVersion.setArchived(true);
            documentVersionRepository.save(latestVersion);
        }

        // Create a new active version
        DocumentVersion newVersion = new DocumentVersion(document, nextVersionNumber, newTitle, newDescription, newFilePath);
        document.addVersion(newVersion);

        // Update the document's main fields
        document.setTitle(newTitle);
        document.setDescription(newDescription);
        document.setFilePath(newFilePath);

        documentVersionRepository.save(newVersion);
        return documentRepository.save(document);
    }

    /**
     * Finds a document by id.
     */
    @Transactional(readOnly = true)
    public Optional<Document> findById(Long id) {
        return documentRepository.findById(id);
    }

    /**
     * Finds all versions of a document, ordered by version number descending.
     */
    @Transactional(readOnly = true)
    public List<DocumentVersion> getDocumentVersions(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with id: " + documentId));
        return documentVersionRepository.findByDocumentOrderByVersionNumberDesc(document);
    }
}
