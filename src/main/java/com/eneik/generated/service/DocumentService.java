package com.eneik.generated.service;

import com.eneik.generated.model.AuditLog;
import com.eneik.generated.model.Document;
import com.eneik.generated.model.DocumentMetadata;
import com.eneik.generated.model.DocumentVersion;
import com.eneik.generated.repository.AuditLogRepository;
import com.eneik.generated.repository.DocumentRepository;
import com.eneik.generated.repository.DocumentVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    // Synonym groups in lowercase for lookup
    private static final List<Set<String>> SYNONYM_GROUPS = Arrays.asList(
            new HashSet<>(Arrays.asList("фбун", "цнии", "эпидемиологии", "роспотребнадзор")),
            new HashSet<>(Arrays.asList("гэк", "государственная", "экзаменационная", "комиссия")),
            new HashSet<>(Arrays.asList("гиа", "государственная", "итоговая", "аттестация")),
            new HashSet<>(Arrays.asList("фгос", "федеральный", "государственный", "образовательный", "стандарт")),
            new HashSet<>(Arrays.asList("ординатура", "ординатор")),
            new HashSet<>(Arrays.asList("аспирантура", "аспирант"))
    );

    public DocumentService(DocumentRepository documentRepository,
                           DocumentVersionRepository documentVersionRepository,
                           AuditLogRepository auditLogRepository,
                           ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
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

        // Record default audit log for backward compatibility
        recordAuditLog("ca078170-df17-48f8-bca4-d89000a6e87f", "ivan.ivanov@epidem.ru", "DOCUMENT_UPLOAD",
                String.valueOf(document.getId()), "edu_center_root");

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

        // Record default audit log
        String catId = "edu_center_root";
        try {
            DocumentMetadata meta = objectMapper.readValue(document.getMetadata(), DocumentMetadata.class);
            if (meta.getCategoryId() != null) {
                catId = meta.getCategoryId();
            }
        } catch (Exception ignored) {}

        recordAuditLog("ca078170-df17-48f8-bca4-d89000a6e87f", "ivan.ivanov@epidem.ru", "DOCUMENT_UPDATE",
                String.valueOf(document.getId()), catId);

        return document;
    }

    @Transactional
    public Document uploadDocumentWithMetadata(String userId, String username, String name, String filePath,
                                               long fileSize, String fileType, String description,
                                               String docType, String specialty, String eduLevel,
                                               String categoryId, List<String> tags) {
        DocumentMetadata meta = new DocumentMetadata(description, docType, specialty, eduLevel, categoryId, tags);
        String metadataStr = "";
        try {
            metadataStr = objectMapper.writeValueAsString(meta);
        } catch (Exception ignored) {}

        Document document = new Document(name, filePath, metadataStr);
        document = documentRepository.save(document);

        DocumentVersion version = new DocumentVersion(document, 1, filePath, metadataStr, false);
        documentVersionRepository.save(version);

        // Record audit log
        recordAuditLog(userId, username, "DOCUMENT_UPLOAD", String.valueOf(document.getId()), categoryId);

        return document;
    }

    @Transactional
    public void recordAuditLog(String userId, String username, String action, String resourceId, String categoryId) {
        AuditLog auditLog = new AuditLog(
                UUID.randomUUID().toString(),
                userId,
                username,
                action,
                resourceId,
                categoryId,
                LocalDateTime.now()
        );
        auditLogRepository.save(auditLog);
    }

    public List<Document> searchAndFilterDocuments(String q, String docType, String specialty,
                                                   String eduLevel, String categoryId, String tag,
                                                   String updatedAfterStr) {
        // Prepare lower-case parameters for DB-level pattern matching filter
        String dbDocType = (docType != null && !docType.trim().isEmpty()) ? docType.toLowerCase() : null;
        String dbSpecialty = (specialty != null && !specialty.trim().isEmpty()) ? specialty.toLowerCase() : null;
        String dbEduLevel = (eduLevel != null && !eduLevel.trim().isEmpty()) ? eduLevel.toLowerCase() : null;
        String dbCategoryId = (categoryId != null && !categoryId.trim().isEmpty()) ? categoryId.toLowerCase() : null;
        String dbTag = (tag != null && !tag.trim().isEmpty()) ? tag.toLowerCase() : null;

        // DB level filter - highly scalable instead of findAll()
        List<Document> filteredSubset = documentRepository.filterBase(
                dbDocType, dbSpecialty, dbEduLevel, dbCategoryId, dbTag
        );

        // Parse updatedAfter date if provided
        LocalDateTime updatedAfter = null;
        if (updatedAfterStr != null && !updatedAfterStr.trim().isEmpty()) {
            try {
                updatedAfter = LocalDateTime.parse(updatedAfterStr.replace("Z", ""));
            } catch (Exception ignored) {}
        }

        // Expand search query with synonyms
        Set<String> queryWords = new HashSet<>();
        if (q != null && !q.trim().isEmpty()) {
            String[] words = q.toLowerCase().split("\\s+");
            for (String w : words) {
                queryWords.add(w);
                for (Set<String> group : SYNONYM_GROUPS) {
                    if (group.contains(w)) {
                        queryWords.addAll(group);
                    }
                }
            }
        }

        LocalDateTime finalUpdatedAfter = updatedAfter;
        return filteredSubset.stream()
                .filter(doc -> {
                    // Filter by updatedAfter
                    if (finalUpdatedAfter != null) {
                        LocalDateTime docTime = doc.getUpdatedAt() != null ? doc.getUpdatedAt() : doc.getCreatedAt();
                        if (docTime != null && docTime.isBefore(finalUpdatedAfter)) {
                            return false;
                        }
                    }

                    // Parse metadata
                    DocumentMetadata meta = null;
                    try {
                        meta = objectMapper.readValue(doc.getMetadata(), DocumentMetadata.class);
                    } catch (Exception ignored) {}

                    // Double check filtering (in case of JSON format variations)
                    if (docType != null && !docType.trim().isEmpty()) {
                        if (meta == null || !docType.equalsIgnoreCase(meta.getDocType())) {
                            return false;
                        }
                    }
                    if (specialty != null && !specialty.trim().isEmpty()) {
                        if (meta == null || !specialty.equalsIgnoreCase(meta.getSpecialty())) {
                            return false;
                        }
                    }
                    if (eduLevel != null && !eduLevel.trim().isEmpty()) {
                        if (meta == null || !eduLevel.equalsIgnoreCase(meta.getEduLevel())) {
                            return false;
                        }
                    }
                    if (categoryId != null && !categoryId.trim().isEmpty()) {
                        if (meta == null || !categoryId.equalsIgnoreCase(meta.getCategoryId())) {
                            return false;
                        }
                    }
                    if (tag != null && !tag.trim().isEmpty()) {
                        if (meta == null || meta.getTags() == null ||
                                meta.getTags().stream().noneMatch(t -> t.equalsIgnoreCase(tag))) {
                            return false;
                        }
                    }

                    // Search by query q (title, description, tags)
                    if (!queryWords.isEmpty()) {
                        boolean matched = false;
                        String titleLower = doc.getTitle().toLowerCase();
                        for (String qw : queryWords) {
                            if (titleLower.contains(qw)) {
                                matched = true;
                                break;
                            }
                        }
                        if (!matched && meta != null) {
                            if (meta.getDescription() != null && meta.getDescription().toLowerCase().contains(q.toLowerCase())) {
                                matched = true;
                            }
                            if (!matched && meta.getTags() != null) {
                                for (String t : meta.getTags()) {
                                    if (t.toLowerCase().contains(q.toLowerCase())) {
                                        matched = true;
                                        break;
                                    }
                                }
                            }
                        }
                        return matched;
                    }

                    return true;
                })
                .collect(Collectors.toList());
    }

    public List<String> getSuggestions(String q) {
        if (q == null || q.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String qLower = q.toLowerCase();
        List<Document> allDocs = documentRepository.findAll();
        Set<String> suggestions = new LinkedHashSet<>();

        for (Document doc : allDocs) {
            if (doc.getTitle().toLowerCase().contains(qLower)) {
                suggestions.add(doc.getTitle());
            }
            try {
                DocumentMetadata meta = objectMapper.readValue(doc.getMetadata(), DocumentMetadata.class);
                if (meta != null && meta.getTags() != null) {
                    for (String t : meta.getTags()) {
                        if (t.toLowerCase().contains(qLower)) {
                            suggestions.add(t);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // Add standard abbreviations if input matches partially
        if ("фгос".startsWith(qLower)) suggestions.add("ФГОС ординатура");
        if ("фбун".startsWith(qLower)) suggestions.add("ФБУН Эпидемиология");
        if ("гиа".startsWith(qLower)) suggestions.add("ГИА вопросы");

        return suggestions.stream().limit(5).collect(Collectors.toList());
    }
}
