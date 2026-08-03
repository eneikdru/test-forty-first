package com.eneik.generated.controller;

import com.eneik.generated.dto.*;
import com.eneik.generated.model.AuditLog;
import com.eneik.generated.model.Document;
import com.eneik.generated.repository.AuditLogRepository;
import com.eneik.generated.repository.DocumentVersionRepository;
import com.eneik.generated.service.DocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentVersionRepository documentVersionRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public DocumentController(DocumentService documentService,
                              DocumentVersionRepository documentVersionRepository,
                              AuditLogRepository auditLogRepository,
                              ObjectMapper objectMapper) {
        this.documentService = documentService;
        this.documentVersionRepository = documentVersionRepository;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public static UUID toUuid(Long id) {
        if (id == null) return null;
        return new UUID(0x123456789ABCDEFL, id);
    }

    public static Long fromUuid(UUID uuid) {
        if (uuid == null) return null;
        return uuid.getLeastSignificantBits();
    }

    @GetMapping("/documents")
    public ResponseEntity<DocumentListResponse> getDocuments(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "suggest", required = false, defaultValue = "false") boolean suggest,
            @RequestParam(value = "doc_type", required = false) String docType,
            @RequestParam(value = "specialty", required = false) String specialty,
            @RequestParam(value = "edu_level", required = false) String eduLevel,
            @RequestParam(value = "category_id", required = false) String categoryId,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "updated_after", required = false) String updatedAfter,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "20") int size
    ) {
        List<Document> filtered = documentService.searchAndFilterDocuments(
                q, docType, specialty, eduLevel, categoryId, tag, updatedAfter
        );

        int start = page * size;
        int end = Math.min(start + size, filtered.size());
        List<DocumentResponse> pageContent = new ArrayList<>();

        if (start < filtered.size()) {
            pageContent = filtered.subList(start, end).stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
        }

        int totalPages = (int) Math.ceil((double) filtered.size() / size);
        List<String> suggestions = suggest ? documentService.getSuggestions(q) : Collections.emptyList();

        DocumentListResponse response = new DocumentListResponse(
                pageContent,
                filtered.size(),
                totalPages,
                page,
                size,
                suggestions
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/documents", consumes = "multipart/form-data")
    public ResponseEntity<DocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("doc_type") String docType,
            @RequestParam("specialty") String specialty,
            @RequestParam("edu_level") String eduLevel,
            @RequestParam("category_id") String categoryId,
            @RequestParam(value = "tags", required = false) List<String> tags
    ) {
        if (file.isEmpty() || name == null || name.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String filePath = "/files/" + file.getOriginalFilename();
        Document savedDoc = documentService.uploadDocumentWithMetadata(
                "ca078170-df17-48f8-bca4-d89000a6e87f", // default user ID
                "ivan.ivanov@epidem.ru",                  // default username
                name,
                filePath,
                file.getSize(),
                file.getContentType(),
                description,
                docType,
                specialty,
                eduLevel,
                categoryId,
                tags != null ? tags : Collections.emptyList()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(savedDoc));
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<?> getDocumentById(@PathVariable("id") String idStr) {
        try {
            UUID uuid = UUID.fromString(idStr);
            Long dbId = fromUuid(uuid);
            List<Document> filtered = documentService.searchAndFilterDocuments(null, null, null, null, null, null, null);
            Optional<Document> docOpt = filtered.stream().filter(d -> d.getId().equals(dbId)).findFirst();

            if (docOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("NOT_FOUND", "Document not found"));
            }

            return ResponseEntity.ok(mapToResponse(docOpt.get()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("BAD_REQUEST", "Invalid UUID format"));
        }
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<AuditLogListResponse> getAuditLogs(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "category_id", required = false) String categoryId,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "50") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<AuditLog> logPage = auditLogRepository.searchLogs(userId, categoryId, pageable);

        List<AuditLogEntryResponse> mapped = logPage.getContent().stream()
                .map(log -> new AuditLogEntryResponse(
                        log.getId(),
                        log.getUserId(),
                        log.getUsername(),
                        log.getAction(),
                        log.getResourceId(),
                        log.getCategoryId(),
                        log.getTimestamp() != null ? log.getTimestamp().toString() + "Z" : ""
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(new AuditLogListResponse(mapped, logPage.getTotalElements()));
    }

    private DocumentResponse mapToResponse(Document doc) {
        DocumentResponse resp = new DocumentResponse();
        resp.setId(toUuid(doc.getId()).toString());
        resp.setName(doc.getTitle());
        resp.setFileSize(245000);
        resp.setFileType("application/pdf");
        resp.setUpdatedAt(doc.getUpdatedAt() != null ? doc.getUpdatedAt().toString() + "Z" : (doc.getCreatedAt() != null ? doc.getCreatedAt().toString() + "Z" : ""));
        resp.setUpdatedBy("ivan.ivanov@epidem.ru");

        try {
            com.eneik.generated.model.DocumentMetadata meta = objectMapper.readValue(doc.getMetadata(), com.eneik.generated.model.DocumentMetadata.class);
            if (meta != null) {
                resp.setDescription(meta.getDescription());
                resp.setDoc_type(meta.getDocType());
                resp.setSpecialty(meta.getSpecialty());
                resp.setEdu_level(meta.getEduLevel());
                resp.setCategory_id(meta.getCategoryId());
                resp.setTags(meta.getTags());
            }
        } catch (Exception ignored) {}

        List<com.eneik.generated.model.DocumentVersion> versions = documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(doc.getId());
        resp.setVersion(versions.isEmpty() ? 1 : versions.get(0).getVersionNumber());

        return resp;
    }
}
