package com.eneik.generated.controller;

import com.eneik.generated.model.Document;
import com.eneik.generated.model.DocumentVersion;
import com.eneik.generated.model.AuditLog;
import com.eneik.generated.repository.DocumentRepository;
import com.eneik.generated.repository.DocumentVersionRepository;
import com.eneik.generated.repository.AuditLogRepository;
import com.eneik.generated.repository.CommentRepository;
import com.eneik.generated.repository.ActualizationRequestRepository;
import com.eneik.generated.model.Comment;
import com.eneik.generated.model.ActualizationRequest;
import com.eneik.generated.service.DocumentService;
import com.eneik.generated.service.FileStorageService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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

    @Value("${security.default.username}")
    private String defaultUsername;

    @Value("${security.default.user-id}")
    private String defaultUserId;

    @Value("${security.default.full-name}")
    private String defaultFullName;

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    private static final String DEFAULT_ROLE = "Administrator";
    private static final String DEFAULT_CATEGORY_ID = "edu_center_root";

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final AuditLogRepository auditLogRepository;
    private final DocumentService documentService;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final CommentRepository commentRepository;
    private final ActualizationRequestRepository actualizationRequestRepository;

    public DocumentController(DocumentRepository documentRepository,
                              DocumentVersionRepository documentVersionRepository,
                              AuditLogRepository auditLogRepository,
                              DocumentService documentService,
                              FileStorageService fileStorageService,
                              ObjectMapper objectMapper,
                              CommentRepository commentRepository,
                              ActualizationRequestRepository actualizationRequestRepository) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.auditLogRepository = auditLogRepository;
        this.documentService = documentService;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
        this.commentRepository = commentRepository;
        this.actualizationRequestRepository = actualizationRequestRepository;
    }

    private static class UserPrincipal {
        private final String username;
        private final String userId;
        private final String fullName;
        private final String role;

        public UserPrincipal(String username, String userId, String fullName, String role) {
            this.username = username;
            this.userId = userId;
            this.fullName = fullName;
            this.role = role;
        }

        public String getUsername() { return username; }
        public String getUserId() { return userId; }
        public String getFullName() { return fullName; }
        public String getRole() { return role; }
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public static class UnauthorizedException extends RuntimeException {
        private final String error;

        public UnauthorizedException(String message) {
            super(message);
            this.error = "UNAUTHORIZED";
        }

        public UnauthorizedException(String error, String message) {
            super(message);
            this.error = error;
        }

        public String getError() {
            return error;
        }
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, String>> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", ex.getError(), "message", ex.getMessage()));
    }

    private String sign(String data, String secret) {
        try {
            javax.crypto.Mac sha256_HMAC = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secretKey);
            byte[] hash = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            log.error("[DocumentController] HMAC-SHA256 signature generation failed", e);
            throw new RuntimeException("Failed to sign data", e);
        }
    }

    private UserPrincipal parseAuthHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("MISSING_TOKEN", "Authorization header is missing or malformed");
        }
        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            throw new UnauthorizedException("MISSING_TOKEN", "Authorization token is empty");
        }

        try {
            String[] parts = token.split("\\.");
            if (parts.length == 3 && token.startsWith("eyJ")) {
                // Verify JWT signature
                String headerAndPayload = parts[0] + "." + parts[1];
                String expectedSignature = sign(headerAndPayload, jwtSecret);

                if (MessageDigest.isEqual(
                        expectedSignature.getBytes(StandardCharsets.UTF_8),
                        parts[2].getBytes(StandardCharsets.UTF_8))) {

                    // Decribute / decode payload
                    byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
                    Map<String, Object> claims = objectMapper.readValue(payloadBytes, new TypeReference<Map<String, Object>>() {});

                    // Check expiration
                    Number expNum = (Number) claims.get("exp");
                    if (expNum != null && System.currentTimeMillis() / 1000 > expNum.longValue()) {
                        log.warn("[DocumentController] JWT token has expired");
                        throw new UnauthorizedException("TOKEN_EXPIRED", "Token has expired");
                    }

                    String sub = (String) claims.get("sub");
                    String userId = (String) claims.get("userId");
                    String fullName = (String) claims.get("fullName");
                    String role = (String) claims.get("role");

                    return new UserPrincipal(
                            sub != null ? sub : defaultUsername,
                            userId != null ? userId : defaultUserId,
                            fullName != null ? fullName : defaultFullName,
                            role != null ? role : DEFAULT_ROLE
                    );
                } else {
                    log.warn("[DocumentController] JWT token signature verification failed");
                    throw new UnauthorizedException("INVALID_TOKEN", "Signature verification failed");
                }
            }
        } catch (UnauthorizedException ue) {
            throw ue;
        } catch (Exception e) {
            log.warn("[DocumentController] Failed to parse Bearer token as JWT: {}", token, e);
        }

        throw new UnauthorizedException("INVALID_TOKEN", "Token parsing failed or signature is invalid");
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
        response.put("updatedBy", meta.getOrDefault("updatedBy", defaultUsername));

        return response;
    }

    // Auth endpoints
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        if (username == null || username.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "BAD_REQUEST", "message", "Username is required"));
        }
        if (password == null || password.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "BAD_REQUEST", "message", "Password is required"));
        }

        long now = System.currentTimeMillis() / 1000;
        long exp = now + 86400; // 24 hours validity

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", username);
        claims.put("userId", defaultUserId);

        String fullName;
        if (username.equals(defaultUsername)) {
            fullName = defaultFullName;
        } else {
            String pre = username.split("@")[0].replace(".", " ");
            StringBuilder sb = new StringBuilder();
            for (String part : pre.split(" ")) {
                if (!part.isEmpty()) {
                    sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
                }
            }
            fullName = sb.toString().trim();
        }

        claims.put("fullName", fullName);
        claims.put("role", DEFAULT_ROLE);
        claims.put("iat", now);
        claims.put("exp", exp);

        String token;
        try {
            String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsString(claims).getBytes(StandardCharsets.UTF_8));
            String signature = sign(header + "." + payload, jwtSecret);
            token = header + "." + payload + "." + signature;
        } catch (Exception e) {
            log.error("[DocumentController] Failed to generate JWT", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "SERVER_ERROR", "message", "Internal error"));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", token);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", defaultUserId);
        user.put("username", username);
        user.put("fullName", fullName);
        user.put("role", DEFAULT_ROLE);

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
                        if (!doc_level_matches(doc, edu_level)) {
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

    private boolean doc_level_matches(Map<String, Object> doc, String edu_level) {
        return edu_level.equalsIgnoreCase((String) doc.get("edu_level"));
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

        UserPrincipal principal = parseAuthHeader(authHeader);
        String username = principal.getUsername();
        String userId = principal.getUserId();

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "BAD_REQUEST", "message", "File is empty"));
        }

        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "BAD_REQUEST", "message", "Document name is required"));
        }

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

        UserPrincipal principal = parseAuthHeader(authHeader);
        String username = principal.getUsername();
        String userId = principal.getUserId();

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

        UserPrincipal principal = parseAuthHeader(authHeader);
        String username = principal.getUsername();
        String userId = principal.getUserId();

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
                    responseVer.put("updatedBy", meta.getOrDefault("updatedBy", defaultUsername));
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

    private Map<String, Object> mapCommentToResponse(Comment comment) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", comment.getUserId());
        user.put("username", comment.getUsername());
        user.put("fullName", comment.getFullName());
        user.put("role", comment.getUserRole());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", longToUuidString(comment.getId()));
        response.put("documentId", longToUuidString(comment.getDocument().getId()));
        response.put("user", user);
        response.put("text", comment.getText());

        LocalDateTime created = comment.getCreatedAt();
        if (created == null) {
            created = LocalDateTime.now();
        }
        response.put("createdAt", created.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
        return response;
    }

    private Map<String, Object> mapActualizationToResponse(ActualizationRequest req) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", req.getRequesterId());
        user.put("username", req.getRequesterUsername());
        user.put("fullName", req.getRequesterFullName());
        user.put("role", req.getRequesterRole());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("requestId", longToUuidString(req.getId()));
        response.put("documentId", longToUuidString(req.getDocument().getId()));
        response.put("requester", user);
        response.put("reason", req.getReason());
        response.put("status", req.getStatus());

        LocalDateTime created = req.getCreatedAt();
        if (created == null) {
            created = LocalDateTime.now();
        }
        response.put("createdAt", created.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
        return response;
    }

    @GetMapping("/documents/{id}/comments")
    public ResponseEntity<?> getDocumentComments(@PathVariable String id) {
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

        List<Comment> comments = commentRepository.findByDocumentId(docId);
        List<Map<String, Object>> response = comments.stream()
                .map(this::mapCommentToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/documents/{id}/comments")
    public ResponseEntity<?> addDocumentComment(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        UserPrincipal principal = parseAuthHeader(authHeader);
        String username = principal.getUsername();
        String userId = principal.getUserId();
        String fullName = principal.getFullName();
        String role = principal.getRole();

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

        String text = body != null ? body.get("text") : null;
        if (text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "BAD_REQUEST", "message", "Comment text is required"));
        }

        Comment comment = new Comment(docOpt.get(), userId, username, fullName, role, text);
        comment = commentRepository.save(comment);

        return ResponseEntity.status(HttpStatus.CREATED).body(mapCommentToResponse(comment));
    }

    @PostMapping("/documents/{id}/actualization-request")
    public ResponseEntity<?> addActualizationRequest(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        UserPrincipal principal = parseAuthHeader(authHeader);
        String username = principal.getUsername();
        String userId = principal.getUserId();
        String fullName = principal.getFullName();
        String role = principal.getRole();

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

        String reason = body != null ? body.get("reason") : null;
        if (reason == null || reason.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "BAD_REQUEST", "message", "Actualization reason is required"));
        }

        ActualizationRequest req = new ActualizationRequest(docOpt.get(), userId, username, fullName, role, reason, "PENDING");
        req = actualizationRequestRepository.save(req);

        return ResponseEntity.status(HttpStatus.CREATED).body(mapActualizationToResponse(req));
    }
}
