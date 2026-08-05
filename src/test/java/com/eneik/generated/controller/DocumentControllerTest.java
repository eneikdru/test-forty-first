package com.eneik.generated.controller;

import com.eneik.generated.model.Document;
import com.eneik.generated.model.AuditLog;
import com.eneik.generated.model.DocumentVersion;
import com.eneik.generated.repository.DocumentRepository;
import com.eneik.generated.repository.AuditLogRepository;
import com.eneik.generated.repository.DocumentVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        // Clear repositories inside Transactional boundary
        auditLogRepository.deleteAll();
        documentVersionRepository.deleteAll();
        documentRepository.deleteAll();
    }

    @Test
    public void testUploadDocumentEmptyNameRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fgos_test.pdf",
                "application/pdf",
                "Mock PDF content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .param("name", "   ") // empty name
                        .param("description", "Учебные материалы по ФГОС")
                        .param("doc_type", "Regulations")
                        .param("specialty", "Epidemiology")
                        .param("edu_level", "Residency")
                        .param("category_id", "edu_center_root")
                        .param("tags", "ординатура", "нормативные акты")
                        .header("Authorization", "Bearer ivan.ivanov@epidem.ru"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    public void testUploadDocumentSuccessAndAuditLogged() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fgos_test.pdf",
                "application/pdf",
                "Mock PDF content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .param("name", "ФГОС Эпидемиология")
                        .param("description", "Учебные материалы по ФГОС")
                        .param("doc_type", "Regulations")
                        .param("specialty", "Epidemiology")
                        .param("edu_level", "Residency")
                        .param("category_id", "edu_center_root")
                        .param("tags", "ординатура", "нормативные акты")
                        .header("Authorization", "Bearer ivan.ivanov@epidem.ru"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("ФГОС Эпидемиология"))
                .andExpect(jsonPath("$.doc_type").value("Regulations"))
                .andExpect(jsonPath("$.version").value(1));

        // Verify Document is saved in database
        List<Document> docs = documentRepository.findAll();
        assertEquals(1, docs.size());
        assertEquals("ФГОС Эпидемиология", docs.get(0).getTitle());

        // Verify Audit Log is recorded
        List<AuditLog> logs = auditLogRepository.findAll();
        assertEquals(1, logs.size());
        AuditLog log = logs.get(0);
        assertEquals("ivan.ivanov@epidem.ru", log.getUsername());
        assertEquals("DOCUMENT_UPLOAD", log.getAction());
        assertEquals("edu_center_root", log.getCategoryId());
    }

    @Test
    public void testSynonymSearchUnderTwoSeconds() throws Exception {
        // Prepare some mock documents with synonyms and abbreviations
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "Content".getBytes()
        );

        // Upload Doc 1: Has "ФГОС" in the name
        mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .param("name", "ФГОС ВО по ординатуре")
                        .param("description", "Официальные стандарты")
                        .param("doc_type", "Regulations")
                        .param("specialty", "Epidemiology")
                        .param("edu_level", "Residency")
                        .param("category_id", "edu_center_root")
                        .param("tags", "ФГОС"))
                .andExpect(status().isCreated());

        // Upload Doc 2: Has long name synonym "Федеральный государственный образовательный стандарт"
        mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .param("name", "Регламент образовательного центра")
                        .param("description", "Этот документ разработан под Федеральный государственный образовательный стандарт.")
                        .param("doc_type", "Regulations")
                        .param("specialty", "Infectious Diseases")
                        .param("edu_level", "Postgraduate")
                        .param("category_id", "edu_center_root")
                        .param("tags", "стандарт"))
                .andExpect(status().isCreated());

        // 1. Synonym test: Search for "ФГОС" -> should match BOTH documents
        // because "ФГОС" expands to "Федеральный государственный образовательный стандарт", which matches Doc 2!
        long startTime = System.currentTimeMillis();

        mockMvc.perform(get("/api/v1/documents")
                        .param("q", "ФГОС"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        long duration = System.currentTimeMillis() - startTime;
        assertTrue(duration < 2000, "Search query with synonyms took " + duration + "ms, which is over the 2-second limit!");

        // 2. Bidirectional synonym test: Search for long form "Федеральный государственный образовательный стандарт"
        // should match BOTH because long form expands / matches "ФГОС", matching Doc 1!
        mockMvc.perform(get("/api/v1/documents")
                        .param("q", "Федеральный государственный образовательный стандарт"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    public void testDocumentVersionHistoryAndUpdates() throws Exception {
        // 1. Upload initial document V1
        MockMultipartFile file1 = new MockMultipartFile(
                "file", "v1.pdf", "application/pdf", "V1 Data".getBytes()
        );

        String responseStr = mockMvc.perform(multipart("/api/v1/documents")
                        .file(file1)
                        .param("name", "Методичка ГИА")
                        .param("description", "ГИА подготовка")
                        .param("doc_type", "Guidelines")
                        .param("specialty", "Other")
                        .param("edu_level", "Residency")
                        .param("category_id", "edu_academic_reports"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> docObj = objectMapper.readValue(responseStr, Map.class);
        String uuid = (String) docObj.get("id");

        // 2. Update to V2
        MockMultipartFile file2 = new MockMultipartFile(
                "file", "v2.pdf", "application/pdf", "V2 Data updated".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/documents/" + uuid)
                        .file(file2)
                        .param("name", "Методичка ГИА v2")
                        .param("description", "Обновленная методичка")
                        .param("version_comment", "Update comment for GIA")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.name").value("Методичка ГИА v2"));

        // 3. Query versions endpoint
        mockMvc.perform(get("/api/v1/documents/" + uuid + "/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].versionNumber").value(2))
                .andExpect(jsonPath("$[0].versionComment").value("Update comment for GIA"))
                .andExpect(jsonPath("$[1].versionNumber").value(1));
    }

    @Test
    public void testDocumentCommentsSuccess() throws Exception {
        // Create document first
        Document document = new Document("Test Document for Feedback", "/path/to/doc.pdf", "{}");
        document = documentRepository.save(document);
        String uuid = new java.util.UUID(0, document.getId()).toString();

        // 1. Post comment
        Map<String, String> requestBody = Map.of("text", "Прошу проверить соответствие новой редакции ФГОС.");
        mockMvc.perform(post("/api/v1/documents/" + uuid + "/comments")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(requestBody))
                        .header("Authorization", "Bearer ivan.ivanov@epidem.ru"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.documentId").value(uuid))
                .andExpect(jsonPath("$.text").value("Прошу проверить соответствие новой редакции ФГОС."))
                .andExpect(jsonPath("$.user.username").value("ivan.ivanov@epidem.ru"))
                .andExpect(jsonPath("$.user.fullName").value("Иванов Иван Иванович"))
                .andExpect(jsonPath("$.user.role").value("Administrator"));

        // 2. Get comments
        mockMvc.perform(get("/api/v1/documents/" + uuid + "/comments")
                        .header("Authorization", "Bearer ivan.ivanov@epidem.ru"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].text").value("Прошу проверить соответствие новой редакции ФГОС."))
                .andExpect(jsonPath("$[0].user.username").value("ivan.ivanov@epidem.ru"));
    }

    @Test
    public void testDocumentCommentsValidationAndNotFound() throws Exception {
        String nonExistentUuid = java.util.UUID.randomUUID().toString();

        // 1. Post to non-existent document
        Map<String, String> requestBody = Map.of("text", "Some comment text.");
        mockMvc.perform(post("/api/v1/documents/" + nonExistentUuid + "/comments")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isNotFound());

        // 2. Get comments from non-existent document
        mockMvc.perform(get("/api/v1/documents/" + nonExistentUuid + "/comments"))
                .andExpect(status().isNotFound());

        // 3. Create a document to test invalid inputs
        Document document = new Document("Test Doc", "/path.pdf", "{}");
        document = documentRepository.save(document);
        String uuid = new java.util.UUID(0, document.getId()).toString();

        // 4. Missing text field
        mockMvc.perform(post("/api/v1/documents/" + uuid + "/comments")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest());

        // 5. Empty text field
        mockMvc.perform(post("/api/v1/documents/" + uuid + "/comments")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("text", "   "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testActualizationRequestSuccess() throws Exception {
        // Create document first
        Document document = new Document("Test Document for Actualization", "/path/to/doc.pdf", "{}");
        document = documentRepository.save(document);
        String uuid = new java.util.UUID(0, document.getId()).toString();

        // Post actualization request
        Map<String, String> requestBody = Map.of("reason", "Приказ Минздрава изменил требования к разделу Эпидемиология.");
        mockMvc.perform(post("/api/v1/documents/" + uuid + "/actualization-request")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(requestBody))
                        .header("Authorization", "Bearer petr.petrov@epidem.ru"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.documentId").value(uuid))
                .andExpect(jsonPath("$.reason").value("Приказ Минздрава изменил требования к разделу Эпидемиология."))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requester.username").value("petr.petrov@epidem.ru"))
                .andExpect(jsonPath("$.requester.fullName").value("Петров Петр Петрович"))
                .andExpect(jsonPath("$.requester.role").value("Administrator"));
    }

    @Test
    public void testActualizationRequestValidationAndNotFound() throws Exception {
        String nonExistentUuid = java.util.UUID.randomUUID().toString();

        // 1. Post actualization request on non-existent document
        Map<String, String> requestBody = Map.of("reason", "Some reason text.");
        mockMvc.perform(post("/api/v1/documents/" + nonExistentUuid + "/actualization-request")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isNotFound());

        // 2. Create a document to test invalid inputs
        Document document = new Document("Test Doc", "/path.pdf", "{}");
        document = documentRepository.save(document);
        String uuid = new java.util.UUID(0, document.getId()).toString();

        // 3. Missing reason field
        mockMvc.perform(post("/api/v1/documents/" + uuid + "/actualization-request")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest());

        // 4. Empty reason field
        mockMvc.perform(post("/api/v1/documents/" + uuid + "/actualization-request")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("reason", "   "))))
                .andExpect(status().isBadRequest());
    }
}
