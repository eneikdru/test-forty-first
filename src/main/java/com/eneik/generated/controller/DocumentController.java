package com.eneik.generated.controller;

import com.eneik.generated.model.Document;
import com.eneik.generated.model.DocumentVersion;
import com.eneik.generated.model.AuditLog;
import com.eneik.generated.repository.DocumentRepository;
import com.eneik.generated.repository.DocumentVersionRepository;
import com.eneik.generated.repository.AuditLogRepository;
import com.eneik.generated.service.DocumentService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final AuditLogRepository auditLogRepository;
    private final DocumentService documentService;
    private final ObjectMapper objectMapper;

    private static final String UPLOAD_DIR = "uploads";

    public DocumentController(DocumentRepository documentRepository,
                              DocumentVersionRepository documentVersionRepository,
                              AuditLogRepository auditLogRepository,
                              DocumentService documentService,
                              ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.auditLogRepository = auditLogRepository;
        this.documentService = documentService;
        this.objectMapper = objectMapper;

        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
        } catch (IOException e) {
            // Log or ignore
        }
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
            return new HashMap<>();
        }
    }

    private String serializeMetadata(Map<String, Object> metaMap) {
        try {
            return objectMapper.writeValueAsString(metaMap);
        } catch (IOException e) {
            return "{}";
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
        response.put("category_id", meta.getOrDefault("category_id", "edu_center_root"));
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
        response.put("updatedBy", meta.getOrDefault("updatedBy", "ivan.ivanov@epidem.ru"));

        return response;
    }

    // Auth endpoints
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String username = request.getOrDefault("username", "ivan.ivanov@epidem.ru");
        Map<String, Object> response = new HashMap<>();
        response.put("token", "mock-jwt-token-xyz");

        Map<String, Object> user = new HashMap<>();
        user.put("id", "ca078170-df17-48f8-bca4-d89000a6e87f");
        user.put("username", username);
        user.put("fullName", username.contains("ivan") ? "Иванов Иван Иванович" : "Петров Петр Петрович");
        user.put("role", "Administrator");

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

        String username = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : "ivan.ivanov@epidem.ru";
        String userId = "ca078170-df17-48f8-bca4-d89000a6e87f";

        // Save file locally (simulation or actual write)
        String originalFilename = file.getOriginalFilename();
        String savedFilePath = UPLOAD_DIR + "/" + UUID.randomUUID() + "_" + originalFilename;

        try {
            byte[] bytes = file.getBytes();
            Path path = Paths.get(savedFilePath);
            Files.write(path, bytes);
        } catch (IOException e) {
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

        String username = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : "ivan.ivanov@epidem.ru";
        String userId = "ca078170-df17-48f8-bca4-d89000a6e87f";

        String savedFilePath = doc.getFilePath();
        if (file != null && !file.isEmpty()) {
            String originalFilename = file.getOriginalFilename();
            savedFilePath = UPLOAD_DIR + "/" + UUID.randomUUID() + "_" + originalFilename;
            try {
                byte[] bytes = file.getBytes();
                Files.write(Paths.get(savedFilePath), bytes);
                metaMap.put("fileSize", file.getSize());
                metaMap.put("fileType", file.getContentType() != null ? file.getContentType() : "application/octet-stream");
            } catch (IOException e) {
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

        String username = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : "ivan.ivanov@epidem.ru";
        String userId = "ca078170-df17-48f8-bca4-d89000a6e87f";

        Document doc = docOpt.get();
        Map<String, Object> meta = parseMetadata(doc.getMetadata());
        String categoryId = (String) meta.getOrDefault("category_id", "edu_center_root");

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
                    responseVer.put("updatedBy", meta.getOrDefault("updatedBy", "ivan.ivanov@epidem.ru"));
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
}
