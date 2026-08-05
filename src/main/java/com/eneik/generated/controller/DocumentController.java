package com.eneik.generated.controller;

import com.eneik.generated.model.Document;
import com.eneik.generated.model.DocumentVersion;
import com.eneik.generated.model.AuditLog;
import com.eneik.generated.repository.DocumentRepository;
import com.eneik.generated.repository.DocumentVersionRepository;
import com.eneik.generated.repository.AuditLogRepository;
import com.eneik.generated.repository.CommentRepository;
import com.eneik.generated.repository.ActualizationRequestRepository;
import com.eneik.generated.service.DocumentService;
import com.eneik.generated.service.FileStorageService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    // Named Constants to prevent 'magic' values (Finding 1 & Finding 2)
    private static final String DEFAULT_USERNAME = "ivan.ivanov@epidem.ru";
    private static final String DEFAULT_USER_ID = "ca078170-df17-48f8-bca4-d89000a6e87f";
    private static final String MOCK_JWT_TOKEN = "mock-jwt-token-xyz";
    private static final String DEFAULT_ROLE = "Administrator";
    private static final String DEFAULT_CATEGORY_ID = "edu_center_root";
    private static final String MOCKED_FULL_NAME_IVAN = "Иванов Иван Иванович";
    private static final String MOCKED_FULL_NAME_PETR = "Петров Петр Петрович";

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final AuditLogRepository auditLogRepository;
    private final CommentRepository commentRepository;
    private final ActualizationRequestRepository actualizationRequestRepository;
    private final DocumentService documentService;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    public DocumentController(DocumentRepository documentRepository,
                              DocumentVersionRepository documentVersionRepository,
                              AuditLogRepository auditLogRepository,
                              CommentRepository commentRepository,
                              ActualizationRequestRepository actualizationRequestRepository,
                              DocumentService documentService,
                              FileStorageService fileStorageService,
                              ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.auditLogRepository = auditLogRepository;
        this.commentRepository = commentRepository;
        this.actualizationRequestRepository = actualizationRequestRepository;
        this.documentService = documentService;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
    }

    private String longToUuidString(Long id) {
        if (id == null) return null;
        return new UUID(0, id).toString();
    }

    private Long uuidStringToLong(String uuidStr) {
        if (uuidStr == null) return null;
        try {
            UUID uuid = UUID.fromString(uuidStr);
            return uuid.getLeastSignificantBits();
        } catch (IllegalArgumentException e) {
            try {
                return Long.parseLong(uuidStr);
            } catch (NumberFormatException nfe) {
                return null;
            }
        }
    }

    private Map<String, Object> parseMetadata(String metaStr) {
        if (metaStr == null || metaStr.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(metaStr, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            log.error("[DocumentController] Failed to parse metadata JSON: {}", metaStr, e);
            return new HashMap<>();
        }
    }

    private String serializeMetadata(Map<String, Object> metaMap) {
        try {
            return objectMapper.writeValueAsString(metaMap);
        } catch (IOException e) {
            log.error("[DocumentController] Failed to serialize metadata map: {}", metaMap, e);
            throw new RuntimeException("Failed to serialize metadata", e);
        }
    }

    private List<String> getSearchVariants(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String normalized = query.trim().toLowerCase();
        List<String> variants = new ArrayList<>();
        variants.add(normalized);

        Map<String, String> synonyms = Map.of(
            "фбун", "фбун цнии эпидемиологии роспотребнадзора",
            "гэк", "государственная экзаменационная комиссия",
            "гиа", "государственная итоговая аттестация",
            "фгос", "федеральный государственный образовательный стандарт"
        );

        for (Map.Entry<String, String> entry : synonyms.entrySet()) {
            String shortForm = entry.getKey();
            String longForm = entry.getValue();

            if (normalized.contains(shortForm)) {
                variants.add(normalized.replace(shortForm, longForm));
            }
            if (normalized.contains(longForm)) {
                variants.add(normalized.replace(longForm, shortForm));
            }
        }
        return variants;
    }

    private Map<String, Object> mapDocumentToResponse(Document doc) {
        Map<String, Object> meta = parseMetadata(doc.getMetadata());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", longToUuidString(doc.getId()));
        response.put("name", doc.getTitle());
        response.put("description", meta.getOrDefault("description", ""));
        response.put("doc_type", meta.getOrDefault("doc_type", "Regulations"));
        response.put("specialty", meta.getOrDefault("specialty", "Other"));
        response.put("edu_level", meta.getOrDefault("edu_level", "Residency"));
        response.put("category_id", meta.getOrDefault("category_id", DEFAULT_CATEGORY_ID));
        response.put("tags", meta.getOrDefault("tags", Collections.emptyList()));

        List<DocumentVersion> versions = documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(doc.getId());
        int versionNum = 1;
        if (!versions.isEmpty()) {
            versionNum = versions.get(0).getVersionNumber();
        }
        response.put("version", versionNum);
        response.put("fileSize", meta.getOrDefault("fileSize", 0));
        response.put("fileType", meta.getOrDefault("fileType", "application/octet-stream"));

        LocalDateTime updated = doc.getUpdatedAt();
        if (updated == null) {
            updated = LocalDateTime.now();
        }
        response.put("updatedAt", updated.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
        response.put("updatedBy", meta.getOrDefault("updatedBy", DEFAULT_USERNAME));

        return response;
    }

    // Auth endpoints
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String username = request.getOrDefault("username", DEFAULT_USERNAME);
        String password = request.getOrDefault("password", "");
        String role = request.getOrDefault("role", DEFAULT_ROLE);

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "UNAUTHORIZED", "message", "Имя пользователя и пароль обязательны"));
        }

        String token = username + ":" + role;
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);

        Map<String, Object> user = new HashMap<>();
        user.put("id", DEFAULT_USER_ID);
        user.put("username", username);
        user.put("fullName", username.contains("ivan") ? MOCKED_FULL_NAME_IVAN : MOCKED_FULL_NAME_PETR);
        user.put("role", role);

        response.put("user", user);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.noContent().build();
    }

    // Documents search
    @GetMapping("/documents")
    public ResponseEntity<?> searchDocuments(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean suggest,
            @RequestParam(required = false) String doc_type,
            @RequestParam(required = false) String specialty,
            @RequestParam(required = false) String edu_level,
            @RequestParam(required = false) String category_id,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String updated_after,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {

        List<Document> allDocs = documentRepository.findAll();
        List<Map<String, Object>> filtered = allDocs.stream()
                .map(this::mapDocumentToResponse)
                .filter(doc -> {
                    // Filter doc_type
                    if (doc_type != null && !doc_type.isBlank()) {
                        if (!doc_type.equalsIgnoreCase((String) doc.get("doc_type"))) {
                            return false;
                        }
                    }
                    // Filter specialty
                    if (specialty != null && !specialty.isBlank()) {
                        if (!specialty.equalsIgnoreCase((String) doc.get("specialty"))) {
                            return false;
                        }
                    }
                    // Filter edu_level
                    if (edu_level != null && !edu_level.isBlank()) {
                        if (!edu_level.equalsIgnoreCase((String) doc.get("edu_level"))) {
                            return false;
                        }
                    }
                    // Filter category_id
                    if (category_id != null && !category_id.isBlank()) {
                        if (!category_id.equalsIgnoreCase((String) doc.get("category_id"))) {
                            return false;
                        }
                    }
                    // Filter tag
                    if (tag != null && !tag.isBlank()) {
                        @SuppressWarnings("unchecked")
                        List<String> tags = (List<String>) doc.get("tags");
                        if (tags == null || tags.stream().noneMatch(t -> t.equalsIgnoreCase(tag))) {
                            return false;
                        }
                    }
                    // Filter updated_after
                    if (updated_after != null && !updated_after.isBlank()) {
                        try {
                            LocalDateTime after = LocalDateTime.parse(updated_after, DateTimeFormatter.ISO_DATE_TIME);
                            LocalDateTime docUpdated = LocalDateTime.parse((String) doc.get("updatedAt"), DateTimeFormatter.ISO_INSTANT);
                            if (docUpdated.isBefore(after)) {
                                return false;
                            }
                        } catch (Exception e) {
                            // ignore parse errors
                        }
                    }
                    // Full-text search with synonyms
                    if (q != null && !q.isBlank()) {
                        List<String> queryVariants = getSearchVariants(q);
                        String nameLower = ((String) doc.get("name")).toLowerCase();
                        String descLower = ((String) doc.get("description")).toLowerCase();
                        @SuppressWarnings("unchecked")
                        List<String> docTags = (List<String>) doc.get("tags");
                        List<String> tagsLower = docTags == null ? Collections.emptyList() :
                                docTags.stream().map(String::toLowerCase).collect(Collectors.toList());

                        boolean matchFound = false;
                        for (String variant : queryVariants) {
                            if (nameLower.contains(variant) || descLower.contains(variant) || tagsLower.contains(variant)) {
                                matchFound = true;
                                break;
                            }
                        }
                        if (!matchFound) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // Suggestions logic
        List<String> suggestions = new ArrayList<>();
        if (suggest && q != null && !q.isBlank()) {
            String queryLower = q.toLowerCase();
            if ("фгос".contains(queryLower)) {
                suggestions.add("ФГОС ординатура");
                suggestions.add("ФГОС аспирантура");
            } else if ("гэк".contains(queryLower)) {
                suggestions.add("Шаблон протокола ГЭК для ГИА");
            }
        }

        int totalElements = filtered.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<Map<String, Object>> contentSlice = filtered.subList(fromIndex, toIndex);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", contentSlice);
        response.put("totalElements", totalElements);
        response.put("totalPages", totalPages);
        response.put("pageNumber", page);
        response.put("pageSize", size);
        response.put("suggestions", suggestions);

        return ResponseEntity.ok(response);
    }

    // Upload a new document
    @PostMapping(value = "/documents", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("doc_type") String docType,
            @RequestParam("specialty") String specialty,
            @RequestParam("edu_level") String eduLevel,
            @RequestParam("category_id") String categoryId,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "BAD_REQUEST", "message", "File is empty"));
        }

        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "BAD_REQUEST", "message", "Document name is required"));
        }

        String username = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : DEFAULT_USERNAME;
        String userId = DEFAULT_USER_ID;

        // Save file locally delegating to FileStorageService
        String savedFilePath;
        try {
            savedFilePath = fileStorageService.saveFile(file);
        } catch (IOException e) {
            log.error("[DocumentController] Failed to save uploaded file: {}", file.getOriginalFilename(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "SERVER_ERROR", "message", "Failed to save file: " + e.getMessage()));
        }

        // Construct metadata
        Map<String, Object> metaMap = new LinkedHashMap<>();
        metaMap.put("description", description == null ? "" : description);
        metaMap.put("doc_type", docType);
        metaMap.put("specialty", specialty);
        metaMap.put("edu_level", eduLevel);
        metaMap.put("category_id", categoryId);
        metaMap.put("tags", tags == null ? Collections.emptyList() : tags);
        metaMap.put("fileSize", file.getSize());
        metaMap.put("fileType", file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        metaMap.put("updatedBy", username);

        String metaStr = serializeMetadata(metaMap);

        // Save new document and its V1 version
        Document doc = documentService.saveNewDocument(name, savedFilePath, metaStr);

        // Record Audit Log
        AuditLog auditLog = new AuditLog(userId, username, "DOCUMENT_UPLOAD", longToUuidString(doc.getId()), categoryId, LocalDateTime.now());
        auditLogRepository.save(auditLog);

        return ResponseEntity.status(HttpStatus.CREATED).body(mapDocumentToResponse(doc));
    }

    // Retrieve document details
    @GetMapping("/documents/{id}")
    public ResponseEntity<?> getDocumentDetails(@PathVariable String id) {
        Long docId = uuidStringToLong(id);
        if (docId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", "Document not found"));
        }

        Optional<Document> docOpt = documentRepository.findById(docId);
        if (docOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", "Document not found"));
        }

        return ResponseEntity.ok(mapDocumentToResponse(docOpt.get()));
    }

    // Update document (Create new version)
    @PutMapping(value = "/documents/{id}", consumes = "multipart/form-data")
    public ResponseEntity<?> updateDocument(
            @PathVariable String id,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "doc_type", required = false) String docType,
            @RequestParam(value = "specialty", required = false) String specialty,
            @RequestParam(value = "edu_level", required = false) String eduLevel,
            @RequestParam(value = "category_id", required = false) String categoryId,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @RequestParam(value = "version_comment", required = false) String versionComment,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        Long docId = uuidStringToLong(id);
        if (docId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", "Document not found"));
        }

        Optional<Document> docOpt = documentRepository.findById(docId);
        if (docOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", "Document not found"));
        }

        Document doc = docOpt.get();
        Map<String, Object> metaMap = parseMetadata(doc.getMetadata());

        String username = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : DEFAULT_USERNAME;
        String userId = DEFAULT_USER_ID;

        String savedFilePath = doc.getFilePath();
        if (file != null && !file.isEmpty()) {
            try {
                savedFilePath = fileStorageService.saveFile(file);
                metaMap.put("fileSize", file.getSize());
                metaMap.put("fileType", file.getContentType() != null ? file.getContentType() : "application/octet-stream");
            } catch (IOException e) {
                log.error("[DocumentController] Failed to save updated file: {}", file.getOriginalFilename(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "SERVER_ERROR", "message", "Failed to save file"));
            }
        }

        if (name != null) {
            doc.setTitle(name);
        }
        if (description != null) {
            metaMap.put("description", description);
        }
        if (docType != null) {
            metaMap.put("doc_type", docType);
        }
        if (specialty != null) {
            metaMap.put("specialty", specialty);
        }
        if (eduLevel != null) {
            metaMap.put("edu_level", eduLevel);
        }
        if (categoryId != null) {
            metaMap.put("category_id", categoryId);
        }
        if (tags != null) {
            metaMap.put("tags", tags);
        }
        metaMap.put("updatedBy", username);
        if (versionComment != null) {
            metaMap.put("versionComment", versionComment);
        }

        String metaStr = serializeMetadata(metaMap);

        // Update via DocumentService to archive previous versions and create new version
        Document updatedDoc = documentService.updateDocument(docId, name, savedFilePath, metaStr);

        // Record Audit Log
        AuditLog auditLog = new AuditLog(userId, username, "DOCUMENT_UPDATE", longToUuidString(updatedDoc.getId()), (String) metaMap.get("category_id"), LocalDateTime.now());
        auditLogRepository.save(auditLog);

        return ResponseEntity.ok(mapDocumentToResponse(updatedDoc));
    }

    // Delete document
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<?> deleteDocument(
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        Long docId = uuidStringToLong(id);
        if (docId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", "Document not found"));
        }

        Optional<Document> docOpt = documentRepository.findById(docId);
        if (docOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", "Document not found"));
        }

        String username = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : DEFAULT_USERNAME;
        String userId = DEFAULT_USER_ID;

        Document doc = docOpt.get();
        Map<String, Object> meta = parseMetadata(doc.getMetadata());
        String categoryId = (String) meta.getOrDefault("category_id", DEFAULT_CATEGORY_ID);

        // Delete all versions
        List<DocumentVersion> versions = documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(docId);
        for (DocumentVersion ver : versions) {
            documentVersionRepository.delete(ver);
        }

        documentRepository.delete(doc);

        // Record Audit Log
        AuditLog auditLog = new AuditLog(userId, username, "DOCUMENT_DELETE", id, categoryId, LocalDateTime.now());
        auditLogRepository.save(auditLog);

        return ResponseEntity.noContent().build();
    }

    // Retrieve document version history
    @GetMapping("/documents/{id}/versions")
    public ResponseEntity<?> getDocumentVersions(@PathVariable String id) {
        Long docId = uuidStringToLong(id);
        if (docId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", "Document not found"));
        }

        Optional<Document> docOpt = documentRepository.findById(docId);
        if (docOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", "Document not found"));
        }

        List<DocumentVersion> versions = documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(docId);
        List<Map<String, Object>> response = versions.stream()
                .map(ver -> {
                    Map<String, Object> responseVer = new LinkedHashMap<>();
                    responseVer.put("id", longToUuidString(ver.getId()));
                    responseVer.put("versionNumber", ver.getVersionNumber());
                    LocalDateTime created = ver.getCreatedAt();
                    if (created == null) {
                        created = LocalDateTime.now();
                    }
                    responseVer.put("updatedAt", created.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));

                    Map<String, Object> meta = parseMetadata(ver.getMetadata());
                    responseVer.put("updatedBy", meta.getOrDefault("updatedBy", DEFAULT_USERNAME));
                    responseVer.put("versionComment", meta.getOrDefault("versionComment", "Initial version"));
                    responseVer.put("fileSize", meta.getOrDefault("fileSize", 0));
                    return responseVer;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // Retrieve audit logs
    @GetMapping("/audit-logs")
    public ResponseEntity<?> getAuditLogs(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String category_id,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size) {

        List<AuditLog> allLogs = auditLogRepository.findAll();
        List<Map<String, Object>> filtered = allLogs.stream()
                .filter(log -> {
                    if (userId != null && !userId.isBlank()) {
                        if (!userId.equalsIgnoreCase(log.getUserId())) {
                            return false;
                        }
                    }
                    if (category_id != null && !category_id.isBlank()) {
                        if (!category_id.equalsIgnoreCase(log.getCategoryId())) {
                            return false;
                        }
                    }
                    return true;
                })
                .sorted((l1, l2) -> l2.getTimestamp().compareTo(l1.getTimestamp()))
                .map(log -> {
                    Map<String, Object> logEntry = new LinkedHashMap<>();
                    logEntry.put("id", longToUuidString(log.getId()));
                    logEntry.put("userId", log.getUserId());
                    logEntry.put("username", log.getUsername());
                    logEntry.put("action", log.getAction());
                    logEntry.put("resourceId", log.getResourceId());
                    logEntry.put("category_id", log.getCategoryId());
                    logEntry.put("timestamp", log.getTimestamp().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
                    return logEntry;
                })
                .collect(Collectors.toList());

        int totalElements = filtered.size();
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<Map<String, Object>> logsSlice = filtered.subList(fromIndex, toIndex);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("logs", logsSlice);
        response.put("totalElements", totalElements);

        return ResponseEntity.ok(response);
    }

    // Document Export Endpoints (Finding 11)
    @GetMapping("/documents/{id}/export")
    public ResponseEntity<?> exportDocument(
            @PathVariable String id,
            @RequestParam String format,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (format == null || (!format.equalsIgnoreCase("pdf") && !format.equalsIgnoreCase("docx"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "BAD_REQUEST", "message", "Неверный формат экспорта. Разрешены только pdf и docx."));
        }
        if (format.equalsIgnoreCase("pdf")) {
            return exportDocumentPdf(id, authHeader);
        } else {
            return exportDocumentDocx(id, authHeader);
        }
    }

    @GetMapping("/documents/{id}/export/pdf")
    public ResponseEntity<?> exportDocumentPdf(
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long docId = uuidStringToLong(id);
        if (docId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", "Документ не найден"));
        }

        Optional<Document> docOpt = documentRepository.findById(docId);
        if (docOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", "Документ не найден"));
        }

        Document doc = docOpt.get();
        Map<String, Object> responseMap = mapDocumentToResponse(doc);
        String categoryId = (String) responseMap.getOrDefault("category_id", DEFAULT_CATEGORY_ID);

        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : DEFAULT_USERNAME;
        String username = token;
        String role = DEFAULT_ROLE;
        if (token.contains(":")) {
            String[] parts = token.split(":", 2);
            username = parts[0];
            role = parts[1];
        }

        if (!isAuthorized(role, categoryId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "FORBIDDEN", "message", "Доступ к этой категории документов ограничен."));
        }

        // Record Audit Log
        AuditLog auditLog = new AuditLog(DEFAULT_USER_ID, username, "DOCUMENT_EXPORT", id, categoryId, LocalDateTime.now());
        auditLogRepository.save(auditLog);

        try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            generatePdf(doc, responseMap, out);
            byte[] bytes = out.toByteArray();

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
            headers.setContentDisposition(org.springframework.http.ContentDisposition.attachment().filename("document_" + id + ".pdf", java.nio.charset.StandardCharsets.UTF_8).build());

            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            log.error("[DocumentController] Failed to generate PDF for document {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "SERVER_ERROR", "message", "Ошибка при генерации PDF"));
        }
    }

    @GetMapping("/documents/{id}/export/docx")
    public ResponseEntity<?> exportDocumentDocx(
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long docId = uuidStringToLong(id);
        if (docId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", "Документ не найден"));
        }

        Optional<Document> docOpt = documentRepository.findById(docId);
        if (docOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", "Документ не найден"));
        }

        Document doc = docOpt.get();
        Map<String, Object> responseMap = mapDocumentToResponse(doc);
        String categoryId = (String) responseMap.getOrDefault("category_id", DEFAULT_CATEGORY_ID);

        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : DEFAULT_USERNAME;
        String username = token;
        String role = DEFAULT_ROLE;
        if (token.contains(":")) {
            String[] parts = token.split(":", 2);
            username = parts[0];
            role = parts[1];
        }

        if (!isAuthorized(role, categoryId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "FORBIDDEN", "message", "Доступ к этой категории документов ограничен."));
        }

        // Record Audit Log
        AuditLog auditLog = new AuditLog(DEFAULT_USER_ID, username, "DOCUMENT_EXPORT", id, categoryId, LocalDateTime.now());
        auditLogRepository.save(auditLog);

        try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            generateDocx(doc, responseMap, out);
            byte[] bytes = out.toByteArray();

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
            headers.setContentDisposition(org.springframework.http.ContentDisposition.attachment().filename("document_" + id + ".docx", java.nio.charset.StandardCharsets.UTF_8).build());

            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            log.error("[DocumentController] Failed to generate DOCX for document {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "SERVER_ERROR", "message", "Ошибка при генерации DOCX"));
        }
    }

    // Comment and Actualization Endpoints (Finding 12)
    @PostMapping("/documents/{id}/comments")
    public ResponseEntity<?> postComment(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long docId = uuidStringToLong(id);
        if (docId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", "Документ не найден"));
        }

        Optional<Document> docOpt = documentRepository.findById(docId);
        if (docOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", "Документ не найден"));
        }

        String text = body.get("text");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "BAD_REQUEST", "message", "Текст комментария обязателен"));
        }

        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : DEFAULT_USERNAME;
        String username = token;
        String role = DEFAULT_ROLE;
        if (token.contains(":")) {
            String[] parts = token.split(":", 2);
            username = parts[0];
            role = parts[1];
        }

        Document doc = docOpt.get();
        Map<String, Object> meta = parseMetadata(doc.getMetadata());
        String categoryId = (String) meta.getOrDefault("category_id", DEFAULT_CATEGORY_ID);

        if (!isAuthorized(role, categoryId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "FORBIDDEN", "message", "Доступ к этой категории документов ограничен."));
        }

        String fullName = username.contains("ivan") ? MOCKED_FULL_NAME_IVAN : MOCKED_FULL_NAME_PETR;
        com.eneik.generated.model.Comment comment = new com.eneik.generated.model.Comment(
                doc, DEFAULT_USER_ID, username, fullName, role, text
        );
        comment = commentRepository.save(comment);

        // Record Audit Log
        AuditLog auditLog = new AuditLog(DEFAULT_USER_ID, username, "COMMENT_POST", id, categoryId, LocalDateTime.now());
        auditLogRepository.save(auditLog);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", longToUuidString(comment.getId()));
        response.put("documentId", id);

        Map<String, Object> userMap = new LinkedHashMap<>();
        userMap.put("id", DEFAULT_USER_ID);
        userMap.put("username", username);
        userMap.put("fullName", fullName);
        userMap.put("role", role);
        response.put("user", userMap);
        response.put("text", comment.getText());
        response.put("createdAt", comment.getCreatedAt() != null ? comment.getCreatedAt().toString() : LocalDateTime.now().toString());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/documents/{id}/comments")
    public ResponseEntity<?> getComments(
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long docId = uuidStringToLong(id);
        if (docId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", "Документ не найден"));
        }

        Optional<Document> docOpt = documentRepository.findById(docId);
        if (docOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", "Документ не найден"));
        }

        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : DEFAULT_USERNAME;
        String role = DEFAULT_ROLE;
        if (token.contains(":")) {
            role = token.split(":", 2)[1];
        }

        Document doc = docOpt.get();
        Map<String, Object> meta = parseMetadata(doc.getMetadata());
        String categoryId = (String) meta.getOrDefault("category_id", DEFAULT_CATEGORY_ID);

        if (!isAuthorized(role, categoryId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "FORBIDDEN", "message", "Доступ к этой категории документов ограничен."));
        }

        List<com.eneik.generated.model.Comment> comments = commentRepository.findByDocumentId(docId);
        List<Map<String, Object>> response = comments.stream().map(c -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", longToUuidString(c.getId()));
            map.put("documentId", id);

            Map<String, Object> uMap = new LinkedHashMap<>();
            uMap.put("id", c.getUserId());
            uMap.put("username", c.getUsername());
            uMap.put("fullName", c.getFullName());
            uMap.put("role", c.getUserRole());
            map.put("user", uMap);

            map.put("text", c.getText());
            map.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : LocalDateTime.now().toString());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/documents/{id}/actualization-request")
    public ResponseEntity<?> postActualizationRequest(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long docId = uuidStringToLong(id);
        if (docId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", "Документ не найден"));
        }

        Optional<Document> docOpt = documentRepository.findById(docId);
        if (docOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", "Документ не найден"));
        }

        String reason = body.get("reason");
        if (reason == null || reason.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "BAD_REQUEST", "message", "Причина актуализации обязательна"));
        }

        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : DEFAULT_USERNAME;
        String username = token;
        String role = DEFAULT_ROLE;
        if (token.contains(":")) {
            String[] parts = token.split(":", 2);
            username = parts[0];
            role = parts[1];
        }

        Document doc = docOpt.get();
        Map<String, Object> meta = parseMetadata(doc.getMetadata());
        String categoryId = (String) meta.getOrDefault("category_id", DEFAULT_CATEGORY_ID);

        if (!isAuthorized(role, categoryId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "FORBIDDEN", "message", "Доступ к этой категории документов ограничен."));
        }

        String fullName = username.contains("ivan") ? MOCKED_FULL_NAME_IVAN : MOCKED_FULL_NAME_PETR;
        com.eneik.generated.model.ActualizationRequest req = new com.eneik.generated.model.ActualizationRequest(
                doc, DEFAULT_USER_ID, username, fullName, role, reason, "PENDING"
        );
        req = actualizationRequestRepository.save(req);

        // Record Audit Log
        AuditLog auditLog = new AuditLog(DEFAULT_USER_ID, username, "ACTUALIZATION_REQUEST_POST", id, categoryId, LocalDateTime.now());
        auditLogRepository.save(auditLog);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("requestId", longToUuidString(req.getId()));
        response.put("documentId", id);

        Map<String, Object> uMap = new LinkedHashMap<>();
        uMap.put("id", DEFAULT_USER_ID);
        uMap.put("username", username);
        uMap.put("fullName", fullName);
        uMap.put("role", role);
        response.put("requester", uMap);

        response.put("reason", req.getReason());
        response.put("status", req.getStatus());
        response.put("createdAt", req.getCreatedAt() != null ? req.getCreatedAt().toString() : LocalDateTime.now().toString());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Helper utilities for export & security
    private boolean isAuthorized(String role, String categoryId) {
        if (role == null) {
            return false;
        }
        if (role.equalsIgnoreCase("Administrator")) {
            return true;
        }
        if (categoryId == null || categoryId.isBlank()) {
            return true;
        }

        switch (categoryId) {
            case "edu_budget_finance":
                return role.equalsIgnoreCase("Administrator");
            case "edu_staff_workload":
                return role.equalsIgnoreCase("Content-manager") || role.equalsIgnoreCase("Teacher");
            case "edu_scholarships":
                return role.equalsIgnoreCase("Content-manager") || role.equalsIgnoreCase("Teacher") || role.equalsIgnoreCase("Student");
            case "edu_academic_reports":
                return role.equalsIgnoreCase("Content-manager") || role.equalsIgnoreCase("Teacher");
            default:
                return true;
        }
    }

    private String getDocTypeLabel(String type) {
        if (type == null) return "";
        switch (type) {
            case "Regulations": return "Регламенты / Нормативные акты";
            case "Forms/Templates": return "Формы / Шаблоны";
            case "Protocols": return "Протоколы";
            case "Curriculum": return "Учебные планы";
            case "Guidelines": return "Методические рекомендации";
            default: return type;
        }
    }

    private String getSpecialtyLabel(String spec) {
        if (spec == null) return "";
        switch (spec) {
            case "Epidemiology": return "Эпидемиология";
            case "Infectious Diseases": return "Инфекционные болезни";
            case "Pediatrics": return "Педиатрия";
            case "Other": return "Другое";
            default: return spec;
        }
    }

    private String getEduLevelLabel(String level) {
        if (level == null) return "";
        switch (level) {
            case "Residency": return "Ординатура";
            case "Postgraduate": return "Аспирантура";
            case "Additional Professional Education": return "Доп. проф. образование";
            default: return level;
        }
    }

    private String getCategoryLabel(String categoryId) {
        if (categoryId == null) return "Общая категория";
        switch (categoryId) {
            case "edu_center_root": return "Общая категория";
            case "edu_budget_finance": return "Бюджет и финансы";
            case "edu_staff_workload": return "Кадры и нагрузка";
            case "edu_scholarships": return "Стипендии";
            case "edu_academic_reports": return "Отчетность";
            default: return categoryId;
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }

    private void generatePdf(Document doc, Map<String, Object> responseMap, java.io.OutputStream out) throws Exception {
        String title = doc.getTitle();
        String description = (String) responseMap.getOrDefault("description", "");
        String docType = (String) responseMap.getOrDefault("doc_type", "");
        String specialty = (String) responseMap.getOrDefault("specialty", "");
        String eduLevel = (String) responseMap.getOrDefault("edu_level", "");
        String categoryId = (String) responseMap.getOrDefault("category_id", "");
        String categoryName = getCategoryLabel(categoryId);
        int version = (int) responseMap.getOrDefault("version", 1);
        String updatedBy = (String) responseMap.getOrDefault("updatedBy", "");
        String updatedAt = (String) responseMap.getOrDefault("updatedAt", "");

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n");
        sb.append("<head>\n");
        sb.append("<style>\n");
        sb.append("  body { font-family: 'DejaVu Sans', sans-serif; color: #333333; margin: 30px; line-height: 1.6; }\n");
        sb.append("  h1 { color: #1A365D; font-size: 24px; border-bottom: 2px solid #1A365D; padding-bottom: 10px; margin-bottom: 20px; }\n");
        sb.append("  h2 { color: #1A365D; font-size: 18px; margin-top: 30px; margin-bottom: 10px; border-bottom: 1px solid #ddd; padding-bottom: 5px; }\n");
        sb.append("  table { width: 100%; border-collapse: collapse; margin-top: 20px; margin-bottom: 20px; }\n");
        sb.append("  th, td { padding: 10px; border: 1px solid #dddddd; text-align: left; font-size: 12px; }\n");
        sb.append("  th { background-color: #f2f2f2; color: #1A365D; font-weight: bold; }\n");
        sb.append("  .label { font-weight: bold; color: #1A365D; width: 30%; }\n");
        sb.append("  .footer { margin-top: 50px; font-size: 10px; text-align: center; color: #888; border-top: 1px solid #eee; padding-top: 10px; }\n");
        sb.append("</style>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append("  <h1>Карточка документа: ").append(escapeHtml(title)).append("</h1>\n");
        sb.append("  <table>\n");
        sb.append("    <tr><td class=\"label\">Название:</td><td>").append(escapeHtml(title)).append("</td></tr>\n");
        sb.append("    <tr><td class=\"label\">Аннотация:</td><td>").append(escapeHtml(description)).append("</td></tr>\n");
        sb.append("    <tr><td class=\"label\">Тип документа:</td><td>").append(escapeHtml(getDocTypeLabel(docType))).append("</td></tr>\n");
        sb.append("    <tr><td class=\"label\">Специальность:</td><td>").append(escapeHtml(getSpecialtyLabel(specialty))).append("</td></tr>\n");
        sb.append("    <tr><td class=\"label\">Уровень образования:</td><td>").append(escapeHtml(getEduLevelLabel(eduLevel))).append("</td></tr>\n");
        sb.append("    <tr><td class=\"label\">Категория:</td><td>").append(escapeHtml(categoryName)).append("</td></tr>\n");
        sb.append("    <tr><td class=\"label\">Версия:</td><td>").append(version).append("</td></tr>\n");
        sb.append("    <tr><td class=\"label\">Автор изменений:</td><td>").append(escapeHtml(updatedBy)).append("</td></tr>\n");
        sb.append("    <tr><td class=\"label\">Дата изменения:</td><td>").append(escapeHtml(updatedAt)).append("</td></tr>\n");
        sb.append("  </table>\n");

        sb.append("  <h2>Обсуждения и комментарии</h2>\n");
        List<com.eneik.generated.model.Comment> comments = commentRepository.findByDocumentId(doc.getId());
        if (comments.isEmpty()) {
            sb.append("  <p style=\"font-size: 12px; color: #666;\">Нет комментариев к этому документу.</p>\n");
        } else {
            sb.append("  <table>\n");
            sb.append("    <thead>\n");
            sb.append("      <tr><th style=\"width: 30%;\">Пользователь</th><th>Текст комментария</th></tr>\n");
            sb.append("    </thead>\n");
            sb.append("    <tbody>\n");
            for (com.eneik.generated.model.Comment c : comments) {
                sb.append("      <tr>\n");
                sb.append("        <td><strong>").append(escapeHtml(c.getFullName())).append("</strong><br/><span style=\"font-size: 10px; color: #888;\">").append(escapeHtml(c.getUserRole())).append("</span></td>\n");
                sb.append("        <td>").append(escapeHtml(c.getText())).append("</td>\n");
                sb.append("      </tr>\n");
            }
            sb.append("    </tbody>\n");
            sb.append("  </table>\n");
        }

        sb.append("  <div class=\"footer\">ФБУН ЦНИИ Эпидемиологии Роспотребнадзора — База знаний</div>\n");
        sb.append("</body>\n");
        sb.append("</html>\n");

        com.openhtmltopdf.pdfboxout.PdfRendererBuilder builder = new com.openhtmltopdf.pdfboxout.PdfRendererBuilder();
        builder.useFastMode();

        java.io.File fontFile = new java.io.File("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf");
        if (!fontFile.exists()) {
            fontFile = new java.io.File("/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf");
        }
        if (fontFile.exists()) {
            builder.useFont(fontFile, "DejaVu Sans");
        }

        builder.withHtmlContent(sb.toString(), null);
        builder.toStream(out);
        builder.run();
    }

    private void generateDocx(Document doc, Map<String, Object> responseMap, java.io.OutputStream out) throws Exception {
        String title = doc.getTitle();
        String description = (String) responseMap.getOrDefault("description", "");
        String docType = (String) responseMap.getOrDefault("doc_type", "");
        String specialty = (String) responseMap.getOrDefault("specialty", "");
        String eduLevel = (String) responseMap.getOrDefault("edu_level", "");
        String categoryId = (String) responseMap.getOrDefault("category_id", "");
        String categoryName = getCategoryLabel(categoryId);
        int version = (int) responseMap.getOrDefault("version", 1);
        String updatedBy = (String) responseMap.getOrDefault("updatedBy", "");
        String updatedAt = (String) responseMap.getOrDefault("updatedAt", "");

        try (org.apache.poi.xwpf.usermodel.XWPFDocument document = new org.apache.poi.xwpf.usermodel.XWPFDocument()) {
            org.apache.poi.xwpf.usermodel.XWPFParagraph titlePara = document.createParagraph();
            titlePara.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
            org.apache.poi.xwpf.usermodel.XWPFRun titleRun = titlePara.createRun();
            titleRun.setText("Карточка документа");
            titleRun.setBold(true);
            titleRun.setFontSize(20);
            titleRun.setColor("1A365D");
            titleRun.setFontFamily("DejaVu Sans");

            org.apache.poi.xwpf.usermodel.XWPFParagraph subtitlePara = document.createParagraph();
            subtitlePara.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
            org.apache.poi.xwpf.usermodel.XWPFRun subtitleRun = subtitlePara.createRun();
            subtitleRun.setText(title);
            subtitleRun.setBold(true);
            subtitleRun.setFontSize(16);
            subtitleRun.setColor("1A365D");
            subtitleRun.setFontFamily("DejaVu Sans");

            document.createParagraph();

            org.apache.poi.xwpf.usermodel.XWPFTable table = document.createTable(9, 2);
            table.setWidth("100%");

            setCellText(table.getRow(0).getCell(0), "Название:", true);
            setCellText(table.getRow(0).getCell(1), title, false);

            setCellText(table.getRow(1).getCell(0), "Аннотация:", true);
            setCellText(table.getRow(1).getCell(1), description, false);

            setCellText(table.getRow(2).getCell(0), "Тип документа:", true);
            setCellText(table.getRow(2).getCell(1), getDocTypeLabel(docType), false);

            setCellText(table.getRow(3).getCell(0), "Специальность:", true);
            setCellText(table.getRow(3).getCell(1), getSpecialtyLabel(specialty), false);

            setCellText(table.getRow(4).getCell(0), "Уровень образования:", true);
            setCellText(table.getRow(4).getCell(1), getEduLevelLabel(eduLevel), false);

            setCellText(table.getRow(5).getCell(0), "Категория:", true);
            setCellText(table.getRow(5).getCell(1), categoryName, false);

            setCellText(table.getRow(6).getCell(0), "Версия:", true);
            setCellText(table.getRow(6).getCell(1), String.valueOf(version), false);

            setCellText(table.getRow(7).getCell(0), "Автор изменений:", true);
            setCellText(table.getRow(7).getCell(1), updatedBy, false);

            setCellText(table.getRow(8).getCell(0), "Дата изменения:", true);
            setCellText(table.getRow(8).getCell(1), updatedAt, false);

            document.createParagraph();

            org.apache.poi.xwpf.usermodel.XWPFParagraph commHeaderPara = document.createParagraph();
            org.apache.poi.xwpf.usermodel.XWPFRun commHeaderRun = commHeaderPara.createRun();
            commHeaderRun.setText("Обсуждения и комментарии");
            commHeaderRun.setBold(true);
            commHeaderRun.setFontSize(14);
            commHeaderRun.setColor("1A365D");
            commHeaderRun.setFontFamily("DejaVu Sans");

            List<com.eneik.generated.model.Comment> comments = commentRepository.findByDocumentId(doc.getId());
            if (comments.isEmpty()) {
                org.apache.poi.xwpf.usermodel.XWPFParagraph emptyPara = document.createParagraph();
                org.apache.poi.xwpf.usermodel.XWPFRun emptyRun = emptyPara.createRun();
                emptyRun.setText("Нет комментариев к этому документу.");
                emptyRun.setItalic(true);
                emptyRun.setFontSize(11);
                emptyRun.setFontFamily("DejaVu Sans");
            } else {
                for (com.eneik.generated.model.Comment c : comments) {
                    org.apache.poi.xwpf.usermodel.XWPFParagraph cPara = document.createParagraph();
                    org.apache.poi.xwpf.usermodel.XWPFRun cUserRun = cPara.createRun();
                    cUserRun.setText(c.getFullName() + " (" + c.getUserRole() + "): ");
                    cUserRun.setBold(true);
                    cUserRun.setFontSize(11);
                    cUserRun.setFontFamily("DejaVu Sans");

                    org.apache.poi.xwpf.usermodel.XWPFRun cTextRun = cPara.createRun();
                    cTextRun.setText(c.getText());
                    cTextRun.setFontSize(11);
                    cTextRun.setFontFamily("DejaVu Sans");
                }
            }

            org.apache.poi.xwpf.usermodel.XWPFParagraph footerPara = document.createParagraph();
            footerPara.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
            org.apache.poi.xwpf.usermodel.XWPFRun footerRun = footerPara.createRun();
            footerRun.setText("\nФБУН ЦНИИ Эпидемиологии Роспотребнадзора — База знаний");
            footerRun.setFontSize(9);
            footerRun.setColor("888888");
            footerRun.setFontFamily("DejaVu Sans");

            document.write(out);
        }
    }

    private void setCellText(org.apache.poi.xwpf.usermodel.XWPFTableCell cell, String text, boolean bold) {
        org.apache.poi.xwpf.usermodel.XWPFParagraph para = cell.getParagraphs().isEmpty() ? cell.addParagraph() : cell.getParagraphs().get(0);
        org.apache.poi.xwpf.usermodel.XWPFRun run = para.createRun();
        run.setText(text);
        run.setBold(bold);
        run.setFontFamily("DejaVu Sans");
        run.setFontSize(11);
        if (bold) {
            run.setColor("1A365D");
        }
    }
}
