package com.eneik.generated.controller;

import com.eneik.generated.model.Document;
import com.eneik.generated.model.DocumentVersion;
import com.eneik.generated.model.UserAnalytics;
import com.eneik.generated.repository.DocumentRepository;
import com.eneik.generated.repository.DocumentVersionRepository;
import com.eneik.generated.repository.UserAnalyticsRepository;
import com.eneik.generated.service.DocumentService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class KnowledgeBaseController {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final UserAnalyticsRepository userAnalyticsRepository;
    private final DocumentService documentService;
    private final ObjectMapper objectMapper;

    // In-memory fallback/mock data for favorites, saved-searches and comments to ensure API completeness
    private final List<Map<String, Object>> favorites = Collections.synchronizedList(new ArrayList<>());
    private final List<Map<String, Object>> savedSearches = Collections.synchronizedList(new ArrayList<>());
    private final Map<Long, List<Map<String, Object>>> comments = Collections.synchronizedMap(new HashMap<>());

    public KnowledgeBaseController(DocumentRepository documentRepository,
                                   DocumentVersionRepository documentVersionRepository,
                                   UserAnalyticsRepository userAnalyticsRepository,
                                   DocumentService documentService,
                                   ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.userAnalyticsRepository = userAnalyticsRepository;
        this.documentService = documentService;
        this.objectMapper = objectMapper;
    }

    // Bidirectional helper to convert between database BIGINT ID and OpenAPI standard UUID string
    public static String formatIdToUuid(Long id) {
        if (id == null) return null;
        String hex = Long.toHexString(id);
        String padded = String.format("%32s", hex).replace(' ', '0');
        return padded.substring(0, 8) + "-" +
                padded.substring(8, 12) + "-" +
                padded.substring(12, 16) + "-" +
                padded.substring(16, 20) + "-" +
                padded.substring(20);
    }

    public static Long parseUuidToId(String uuidStr) {
        if (uuidStr == null) return null;
        if (uuidStr.contains("-")) {
            try {
                String clean = uuidStr.replace("-", "").replaceFirst("^0+", "");
                if (clean.isEmpty()) return 0L;
                return Long.parseLong(clean, 16);
            } catch (NumberFormatException e) {
                // fallback
                return Long.parseLong(uuidStr.substring(uuidStr.lastIndexOf("-") + 1));
            }
        }
        return Long.parseLong(uuidStr);
    }

    // --- Authentication ---
    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.getOrDefault("username", "admin@epidem.ru");
        String role = "Administrator";
        if (username.contains("manager")) {
            role = "Content-manager";
        } else if (username.contains("teacher")) {
            role = "Teacher";
        } else if (username.contains("student")) {
            role = "Student";
        }

        Map<String, Object> user = new HashMap<>();
        user.put("id", UUID.randomUUID().toString());
        user.put("username", username);
        user.put("fullName", "Проверенный Пользователь");
        user.put("role", role);

        Map<String, Object> response = new HashMap<>();
        response.put("token", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.mockTokenForEneik");
        response.put("user", user);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }

    // --- Documents: List and Search ---
    @GetMapping("/documents")
    public ResponseEntity<Map<String, Object>> listDocuments(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean suggest,
            @RequestParam(required = false) String doc_type,
            @RequestParam(required = false) String specialty,
            @RequestParam(required = false) String edu_level,
            @RequestParam(required = false) String category_id,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String updated_after,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        List<Document> allDocs = documentRepository.findAll();

        // Filter and map to JSON structure
        List<Map<String, Object>> mappedList = allDocs.stream()
                .map(doc -> {
                    try {
                        Map<String, Object> metadataMap = new HashMap<>();
                        if (doc.getMetadata() != null && !doc.getMetadata().trim().isEmpty()) {
                            metadataMap = objectMapper.readValue(doc.getMetadata(), new TypeReference<Map<String, Object>>() {});
                        }

                        List<DocumentVersion> versions = documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(doc.getId());
                        int versionNum = 1;
                        if (!versions.isEmpty()) {
                            versionNum = versions.get(0).getVersionNumber();
                        }

                        Map<String, Object> res = new HashMap<>();
                        res.put("id", formatIdToUuid(doc.getId()));
                        res.put("name", doc.getTitle());
                        res.put("description", metadataMap.getOrDefault("description", ""));
                        res.put("doc_type", metadataMap.get("doc_type"));
                        res.put("specialty", metadataMap.get("specialty"));
                        res.put("edu_level", metadataMap.get("edu_level"));
                        res.put("category_id", metadataMap.get("category_id"));
                        res.put("tags", metadataMap.getOrDefault("tags", Collections.emptyList()));
                        res.put("version", versionNum);
                        res.put("fileSize", metadataMap.getOrDefault("fileSize", 1024));
                        res.put("fileType", metadataMap.getOrDefault("fileType", "application/pdf"));
                        res.put("updatedAt", doc.getUpdatedAt() != null ? doc.getUpdatedAt().format(DateTimeFormatter.ISO_DATE_TIME) : LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
                        res.put("updatedBy", metadataMap.getOrDefault("updatedBy", "system@epidem.ru"));

                        return res;
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Filter by Query with Synonym Expansion
        if (q != null && !q.trim().isEmpty()) {
            mappedList = mappedList.stream()
                    .filter(docMap -> SynonymSearchHelper.matchesQueryWithSynonyms(docMap, q))
                    .collect(Collectors.toList());
        }

        // Filters
        if (doc_type != null) {
            mappedList = mappedList.stream().filter(d -> doc_type.equalsIgnoreCase((String) d.get("doc_type"))).collect(Collectors.toList());
        }
        if (specialty != null) {
            mappedList = mappedList.stream().filter(d -> specialty.equalsIgnoreCase((String) d.get("specialty"))).collect(Collectors.toList());
        }
        if (edu_level != null) {
            mappedList = mappedList.stream().filter(d -> edu_level.equalsIgnoreCase((String) d.get("edu_level"))).collect(Collectors.toList());
        }
        if (category_id != null) {
            mappedList = mappedList.stream().filter(d -> category_id.equalsIgnoreCase((String) d.get("category_id"))).collect(Collectors.toList());
        }
        if (tag != null) {
            mappedList = mappedList.stream().filter(d -> {
                List<String> tags = (List<String>) d.get("tags");
                return tags != null && tags.stream().anyMatch(t -> t.equalsIgnoreCase(tag));
            }).collect(Collectors.toList());
        }
        if (updated_after != null) {
            try {
                LocalDateTime after = LocalDateTime.parse(updated_after, DateTimeFormatter.ISO_DATE_TIME);
                mappedList = mappedList.stream().filter(d -> {
                    LocalDateTime uAt = LocalDateTime.parse((String) d.get("updatedAt"), DateTimeFormatter.ISO_DATE_TIME);
                    return uAt.isAfter(after);
                }).collect(Collectors.toList());
            } catch (Exception ignored) {}
        }

        // Pagination
        int totalElements = mappedList.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<Map<String, Object>> paginatedContent = mappedList.subList(fromIndex, toIndex);

        // Autocomplete suggestions
        List<String> suggestionsList = new ArrayList<>();
        if (suggest && q != null && !q.trim().isEmpty()) {
            suggestionsList = SynonymSearchHelper.getSuggestions(q);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("content", paginatedContent);
        response.put("totalElements", totalElements);
        response.put("totalPages", totalPages);
        response.put("pageNumber", page);
        response.put("pageSize", size);
        response.put("suggestions", suggestionsList);

        return ResponseEntity.ok(response);
    }

    // --- Upload Document ---
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("doc_type") String docType,
            @RequestParam("specialty") String specialty,
            @RequestParam("edu_level") String eduLevel,
            @RequestParam("category_id") String categoryId,
            @RequestParam(value = "tags", required = false) List<String> tags
    ) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            // Store file to data/uploads
            File uploadDir = new File("./data/uploads");
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }
            File destFile = new File(uploadDir, System.currentTimeMillis() + "_" + file.getOriginalFilename());
            Files.copy(file.getInputStream(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // Save document & first version via DocumentService
            Map<String, Object> metadataMap = new HashMap<>();
            metadataMap.put("description", description != null ? description : "");
            metadataMap.put("doc_type", docType);
            metadataMap.put("specialty", specialty);
            metadataMap.put("edu_level", eduLevel);
            metadataMap.put("category_id", categoryId);
            metadataMap.put("tags", tags != null ? tags : Collections.emptyList());
            metadataMap.put("fileSize", file.getSize());
            metadataMap.put("fileType", file.getContentType());
            metadataMap.put("updatedBy", "admin@epidem.ru");

            String metadataJson = objectMapper.writeValueAsString(metadataMap);

            Document doc = documentService.saveNewDocument(name, destFile.getPath(), metadataJson);

            // Record audit log entry in user_analytics
            Map<String, Object> auditMetadata = new HashMap<>();
            auditMetadata.put("username", "admin@epidem.ru");
            auditMetadata.put("category_id", categoryId);
            auditMetadata.put("name", name);

            UserAnalytics audit = new UserAnalytics(
                    "admin-uuid-111",
                    "DOCUMENT_UPLOAD",
                    formatIdToUuid(doc.getId()),
                    "DOCUMENT",
                    LocalDateTime.now(),
                    objectMapper.writeValueAsString(auditMetadata)
            );
            userAnalyticsRepository.save(audit);

            // Construct JSON Response
            Map<String, Object> response = new HashMap<>();
            response.put("id", formatIdToUuid(doc.getId()));
            response.put("name", doc.getTitle());
            response.put("description", description);
            response.put("doc_type", docType);
            response.put("specialty", specialty);
            response.put("edu_level", eduLevel);
            response.put("category_id", categoryId);
            response.put("tags", tags != null ? tags : Collections.emptyList());
            response.put("version", 1);
            response.put("fileSize", file.getSize());
            response.put("fileType", file.getContentType());
            response.put("updatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
            response.put("updatedBy", "admin@epidem.ru");

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // --- Document Details ---
    @GetMapping("/documents/{id}")
    public ResponseEntity<Map<String, Object>> getDocumentDetails(@PathVariable String id) {
        Long docId = parseUuidToId(id);
        Optional<Document> docOpt = documentRepository.findById(docId);
        if (docOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Document doc = docOpt.get();
        try {
            Map<String, Object> metadataMap = new HashMap<>();
            if (doc.getMetadata() != null && !doc.getMetadata().trim().isEmpty()) {
                metadataMap = objectMapper.readValue(doc.getMetadata(), new TypeReference<Map<String, Object>>() {});
            }

            List<DocumentVersion> versions = documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(doc.getId());
            int versionNum = 1;
            if (!versions.isEmpty()) {
                versionNum = versions.get(0).getVersionNumber();
            }

            Map<String, Object> res = new HashMap<>();
            res.put("id", formatIdToUuid(doc.getId()));
            res.put("name", doc.getTitle());
            res.put("description", metadataMap.getOrDefault("description", ""));
            res.put("doc_type", metadataMap.get("doc_type"));
            res.put("specialty", metadataMap.get("specialty"));
            res.put("edu_level", metadataMap.get("edu_level"));
            res.put("category_id", metadataMap.get("category_id"));
            res.put("tags", metadataMap.getOrDefault("tags", Collections.emptyList()));
            res.put("version", versionNum);
            res.put("fileSize", metadataMap.getOrDefault("fileSize", 1024));
            res.put("fileType", metadataMap.getOrDefault("fileType", "application/pdf"));
            res.put("updatedAt", doc.getUpdatedAt() != null ? doc.getUpdatedAt().format(DateTimeFormatter.ISO_DATE_TIME) : LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
            res.put("updatedBy", metadataMap.getOrDefault("updatedBy", "system@epidem.ru"));

            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // --- Update Document (Create Version) ---
    @PutMapping(value = "/documents/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> updateDocument(
            @PathVariable String id,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "doc_type", required = false) String docType,
            @RequestParam(value = "specialty", required = false) String specialty,
            @RequestParam(value = "edu_level", required = false) String eduLevel,
            @RequestParam(value = "category_id", required = false) String categoryId,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @RequestParam("version_comment") String versionComment
    ) {
        Long docId = parseUuidToId(id);
        Optional<Document> docOpt = documentRepository.findById(docId);
        if (docOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            Document doc = docOpt.get();
            Map<String, Object> metadataMap = new HashMap<>();
            if (doc.getMetadata() != null && !doc.getMetadata().trim().isEmpty()) {
                metadataMap = objectMapper.readValue(doc.getMetadata(), new TypeReference<Map<String, Object>>() {});
            }

            if (description != null) metadataMap.put("description", description);
            if (docType != null) metadataMap.put("doc_type", docType);
            if (specialty != null) metadataMap.put("specialty", specialty);
            if (eduLevel != null) metadataMap.put("edu_level", eduLevel);
            if (categoryId != null) metadataMap.put("category_id", categoryId);
            if (tags != null) metadataMap.put("tags", tags);
            metadataMap.put("updatedBy", "admin@epidem.ru");

            String newFilePath = doc.getFilePath();
            if (file != null && !file.isEmpty()) {
                File uploadDir = new File("./data/uploads");
                if (!uploadDir.exists()) {
                    uploadDir.mkdirs();
                }
                File destFile = new File(uploadDir, System.currentTimeMillis() + "_" + file.getOriginalFilename());
                Files.copy(file.getInputStream(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                newFilePath = destFile.getPath();
                metadataMap.put("fileSize", file.getSize());
                metadataMap.put("fileType", file.getContentType());
            }

            String updatedTitle = name != null ? name : doc.getTitle();
            String metadataJson = objectMapper.writeValueAsString(metadataMap);

            Document updatedDoc = documentService.updateDocument(docId, updatedTitle, newFilePath, metadataJson);

            // Record audit log entry
            Map<String, Object> auditMetadata = new HashMap<>();
            auditMetadata.put("username", "admin@epidem.ru");
            auditMetadata.put("category_id", categoryId != null ? categoryId : metadataMap.get("category_id"));
            auditMetadata.put("comment", versionComment);

            UserAnalytics audit = new UserAnalytics(
                    "admin-uuid-111",
                    "DOCUMENT_UPDATE",
                    id,
                    "DOCUMENT",
                    LocalDateTime.now(),
                    objectMapper.writeValueAsString(auditMetadata)
            );
            userAnalyticsRepository.save(audit);

            List<DocumentVersion> versions = documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(updatedDoc.getId());
            int versionNum = 1;
            if (!versions.isEmpty()) {
                versionNum = versions.get(0).getVersionNumber();
            }

            Map<String, Object> res = new HashMap<>();
            res.put("id", formatIdToUuid(updatedDoc.getId()));
            res.put("name", updatedDoc.getTitle());
            res.put("description", metadataMap.getOrDefault("description", ""));
            res.put("doc_type", metadataMap.get("doc_type"));
            res.put("specialty", metadataMap.get("specialty"));
            res.put("edu_level", metadataMap.get("edu_level"));
            res.put("category_id", metadataMap.get("category_id"));
            res.put("tags", metadataMap.getOrDefault("tags", Collections.emptyList()));
            res.put("version", versionNum);
            res.put("fileSize", metadataMap.getOrDefault("fileSize", 1024));
            res.put("fileType", metadataMap.getOrDefault("fileType", "application/pdf"));
            res.put("updatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
            res.put("updatedBy", "admin@epidem.ru");

            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // --- Delete Document ---
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable String id) {
        Long docId = parseUuidToId(id);
        if (!documentRepository.existsById(docId)) {
            return ResponseEntity.notFound().build();
        }

        documentRepository.deleteById(docId);

        // Record audit log entry
        Map<String, Object> auditMetadata = new HashMap<>();
        auditMetadata.put("username", "admin@epidem.ru");

        UserAnalytics audit = new UserAnalytics(
                "admin-uuid-111",
                "DOCUMENT_DELETE",
                id,
                "DOCUMENT",
                LocalDateTime.now(),
                "Deleted document ID " + id
        );
        userAnalyticsRepository.save(audit);

        return ResponseEntity.noContent().build();
    }

    // --- Versions ---
    @GetMapping("/documents/{id}/versions")
    public ResponseEntity<List<Map<String, Object>>> getDocumentVersions(@PathVariable String id) {
        Long docId = parseUuidToId(id);
        if (!documentRepository.existsById(docId)) {
            return ResponseEntity.notFound().build();
        }

        List<DocumentVersion> versions = documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(docId);
        List<Map<String, Object>> result = versions.stream()
                .map(v -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", formatIdToUuid(v.getId()));
                    m.put("versionNumber", v.getVersionNumber());
                    m.put("updatedAt", v.getCreatedAt() != null ? v.getCreatedAt().format(DateTimeFormatter.ISO_DATE_TIME) : LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
                    m.put("updatedBy", "admin@epidem.ru");
                    m.put("versionComment", "Редакция " + v.getVersionNumber());
                    m.put("fileSize", 1024);
                    return m;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // --- Comments ---
    @GetMapping("/documents/{id}/comments")
    public ResponseEntity<List<Map<String, Object>>> getComments(@PathVariable String id) {
        Long docId = parseUuidToId(id);
        List<Map<String, Object>> list = comments.getOrDefault(docId, Collections.emptyList());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/documents/{id}/comments")
    public ResponseEntity<Map<String, Object>> postComment(@PathVariable String id, @RequestBody Map<String, String> payload) {
        Long docId = parseUuidToId(id);
        if (!documentRepository.existsById(docId)) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> user = new HashMap<>();
        user.put("id", "ca078170-df17-48f8-bca4-d89000a6e87f");
        user.put("username", "ivan.ivanov@epidem.ru");
        user.put("fullName", "Иванов Иван Иванович");
        user.put("role", "Student");

        Map<String, Object> comment = new HashMap<>();
        comment.put("id", UUID.randomUUID().toString());
        comment.put("documentId", id);
        comment.put("user", user);
        comment.put("text", payload.getOrDefault("text", ""));
        comment.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));

        comments.computeIfAbsent(docId, k -> Collections.synchronizedList(new ArrayList<>())).add(comment);

        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }

    // --- Actualization Request ---
    @PostMapping("/documents/{id}/actualization-request")
    public ResponseEntity<Map<String, Object>> actualizationRequest(@PathVariable String id, @RequestBody Map<String, String> payload) {
        Long docId = parseUuidToId(id);
        if (!documentRepository.existsById(docId)) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> user = new HashMap<>();
        user.put("id", "ca078170-df17-48f8-bca4-d89000a6e87f");
        user.put("username", "ivan.ivanov@epidem.ru");
        user.put("fullName", "Иванов Иван Иванович");
        user.put("role", "Student");

        Map<String, Object> res = new HashMap<>();
        res.put("requestId", UUID.randomUUID().toString());
        res.put("documentId", id);
        res.put("requester", user);
        res.put("reason", payload.getOrDefault("reason", ""));
        res.put("status", "PENDING");
        res.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));

        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    // --- Export ---
    @GetMapping("/documents/{id}/export")
    public ResponseEntity<byte[]> exportDocument(@PathVariable String id, @RequestParam String format) {
        Long docId = parseUuidToId(id);
        Optional<Document> docOpt = documentRepository.findById(docId);
        if (docOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Document doc = docOpt.get();
        byte[] dummyBytes = ("Exported content of " + doc.getTitle() + " in " + format + " format.").getBytes();

        String filename = "document." + format;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(dummyBytes);
    }

    // --- Favorites ---
    @GetMapping("/favorites")
    public ResponseEntity<List<Map<String, Object>>> getFavorites() {
        return ResponseEntity.ok(favorites);
    }

    @PostMapping("/favorites")
    public ResponseEntity<Void> addFavorite(@RequestBody Map<String, String> payload) {
        String docIdStr = payload.get("documentId");
        if (docIdStr == null) {
            return ResponseEntity.badRequest().build();
        }

        Long docId = parseUuidToId(docIdStr);
        Optional<Document> docOpt = documentRepository.findById(docId);
        if (docOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        Document doc = docOpt.get();
        try {
            Map<String, Object> metadataMap = new HashMap<>();
            if (doc.getMetadata() != null && !doc.getMetadata().trim().isEmpty()) {
                metadataMap = objectMapper.readValue(doc.getMetadata(), new TypeReference<Map<String, Object>>() {});
            }

            Map<String, Object> fav = new HashMap<>();
            fav.put("id", docIdStr);
            fav.put("name", doc.getTitle());
            fav.put("description", metadataMap.getOrDefault("description", ""));
            fav.put("doc_type", metadataMap.get("doc_type"));
            fav.put("specialty", metadataMap.get("specialty"));
            fav.put("edu_level", metadataMap.get("edu_level"));
            fav.put("category_id", metadataMap.get("category_id"));
            fav.put("tags", metadataMap.getOrDefault("tags", Collections.emptyList()));
            fav.put("version", 1);
            fav.put("fileSize", metadataMap.getOrDefault("fileSize", 1024));
            fav.put("fileType", metadataMap.getOrDefault("fileType", "application/pdf"));
            fav.put("updatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
            fav.put("updatedBy", "admin@epidem.ru");

            favorites.add(fav);
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/favorites/{documentId}")
    public ResponseEntity<Void> removeFavorite(@PathVariable String documentId) {
        favorites.removeIf(fav -> documentId.equals(fav.get("id")));
        return ResponseEntity.noContent().build();
    }

    // --- Saved Searches ---
    @GetMapping("/saved-searches")
    public ResponseEntity<List<Map<String, Object>>> getSavedSearches() {
        return ResponseEntity.ok(savedSearches);
    }

    @PostMapping("/saved-searches")
    public ResponseEntity<Map<String, Object>> saveSearch(@RequestBody Map<String, Object> payload) {
        Map<String, Object> saved = new HashMap<>(payload);
        saved.put("id", UUID.randomUUID().toString());
        saved.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        savedSearches.add(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/saved-searches/{id}")
    public ResponseEntity<Void> deleteSavedSearch(@PathVariable String id) {
        savedSearches.removeIf(s -> id.equals(s.get("id")));
        return ResponseEntity.noContent().build();
    }

    // --- Audit Logs ---
    @GetMapping("/audit-logs")
    public ResponseEntity<Map<String, Object>> getAuditLogs(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String category_id,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size
    ) {
        List<UserAnalytics> list = userAnalyticsRepository.findAll();

        List<Map<String, Object>> logs = list.stream()
                .map(ua -> {
                    try {
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", formatIdToUuid(ua.getId()));
                        m.put("userId", ua.getUserId());

                        String username = "system@epidem.ru";
                        String catId = "edu_center_root";

                        if (ua.getMetadata() != null && ua.getMetadata().startsWith("{")) {
                            Map<String, Object> meta = objectMapper.readValue(ua.getMetadata(), new TypeReference<Map<String, Object>>() {});
                            username = (String) meta.getOrDefault("username", username);
                            catId = (String) meta.getOrDefault("category_id", catId);
                        }

                        m.put("username", username);
                        m.put("action", ua.getActionType());
                        m.put("resourceId", ua.getResourceId());
                        m.put("category_id", catId);
                        m.put("timestamp", ua.getTimestamp() != null ? ua.getTimestamp().format(DateTimeFormatter.ISO_DATE_TIME) : LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));

                        return m;
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (userId != null) {
            logs = logs.stream().filter(l -> userId.equals(l.get("userId"))).collect(Collectors.toList());
        }
        if (category_id != null) {
            logs = logs.stream().filter(l -> category_id.equalsIgnoreCase((String) l.get("category_id"))).collect(Collectors.toList());
        }

        int totalElements = logs.size();
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<Map<String, Object>> paginated = logs.subList(fromIndex, toIndex);

        Map<String, Object> res = new HashMap<>();
        res.put("logs", paginated);
        res.put("totalElements", totalElements);

        return ResponseEntity.ok(res);
    }

    // --- Statistics ---
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        Map<String, Object> res = new HashMap<>();

        List<Map<String, Object>> popularQueries = new ArrayList<>();
        Map<String, Object> q1 = new HashMap<>();
        q1.put("query", "ФБУН");
        q1.put("count", 245);
        popularQueries.add(q1);

        Map<String, Object> q2 = new HashMap<>();
        q2.put("query", "ФГОС");
        q2.put("count", 189);
        popularQueries.add(q2);

        res.put("popularQueries", popularQueries);

        List<Map<String, Object>> mostViewed = new ArrayList<>();
        res.put("mostViewedDocuments", mostViewed);

        List<Map<String, Object>> mostDownloaded = new ArrayList<>();
        res.put("mostDownloadedDocuments", mostDownloaded);

        return ResponseEntity.ok(res);
    }
}
