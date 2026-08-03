package com.eneik.generated.controller;

import com.eneik.generated.model.Document;
import com.eneik.generated.model.DocumentVersion;
import com.eneik.generated.model.UserAnalytics;
import com.eneik.generated.repository.DocumentRepository;
import com.eneik.generated.repository.DocumentVersionRepository;
import com.eneik.generated.repository.UserAnalyticsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class KnowledgeBaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private UserAnalyticsRepository userAnalyticsRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        documentVersionRepository.deleteAll();
        documentRepository.deleteAll();
        userAnalyticsRepository.deleteAll();
    }

    @Test
    public void testAuthLoginReturnsTokenAndUser() throws Exception {
        String loginPayload = "{\"username\":\"manager.test@epidem.ru\",\"password\":\"securePass123\"}";

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.user.username", is("manager.test@epidem.ru")))
                .andExpect(jsonPath("$.user.role", is("Content-manager")));
    }

    @Test
    public void testAuthLogoutReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent());
    }

    @Test
    public void testUploadDocumentAndRecordAuditLogs() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fgos_epidemiology.pdf", "application/pdf", "FGOS Content".getBytes()
        );

        long start = System.currentTimeMillis();

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .param("name", "ФГОС по специальности Эпидемиология")
                        .param("description", "Образовательный стандарт для ординатуры")
                        .param("doc_type", "Regulations")
                        .param("specialty", "Epidemiology")
                        .param("edu_level", "Residency")
                        .param("category_id", "edu_center_root")
                        .param("tags", "ординатура", "стандарты"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("ФГОС по специальности Эпидемиология")))
                .andExpect(jsonPath("$.version", is(1)))
                .andExpect(jsonPath("$.category_id", is("edu_center_root")));

        long duration = System.currentTimeMillis() - start;
        assertTrue(duration < 2000, "Upload and indexing took longer than 2 seconds");

        // Verify Document saved in DB
        List<Document> docs = documentRepository.findAll();
        assertEquals(1, docs.size());
        Document doc = docs.get(0);
        assertEquals("ФГОС по специальности Эпидемиология", doc.getTitle());

        // Verify DocumentVersion saved in DB
        List<DocumentVersion> versions = documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(doc.getId());
        assertEquals(1, versions.size());
        assertEquals(1, versions.get(0).getVersionNumber());

        // Verify Audit Log entry in UserAnalytics table
        List<UserAnalytics> analytics = userAnalyticsRepository.findAll();
        assertEquals(1, analytics.size());
        UserAnalytics audit = analytics.get(0);
        assertEquals("DOCUMENT_UPLOAD", audit.getActionType());
        assertEquals(KnowledgeBaseController.formatIdToUuid(doc.getId()), audit.getResourceId());
        assertTrue(audit.getMetadata().contains("edu_center_root"));
    }

    @Test
    public void testSynonymAwareSearchUnderTwoSeconds() throws Exception {
        // Setup documents with Russian abbreviations and synonyms
        MockMultipartFile file1 = new MockMultipartFile("file", "doc1.pdf", "application/pdf", "Content 1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file", "doc2.pdf", "application/pdf", "Content 2".getBytes());

        // Document 1 uses abbreviation "ФГОС"
        mockMvc.perform(multipart("/api/v1/documents")
                        .file(file1)
                        .param("name", "Новый ФГОС ординатура")
                        .param("description", "Описание стандартов")
                        .param("doc_type", "Regulations")
                        .param("specialty", "Epidemiology")
                        .param("edu_level", "Residency")
                        .param("category_id", "edu_center_root"));

        // Document 2 uses abbreviation "ГИА"
        mockMvc.perform(multipart("/api/v1/documents")
                        .file(file2)
                        .param("name", "Вопросы ГИА 2026")
                        .param("description", "Итоговая аттестация")
                        .param("doc_type", "Regulations")
                        .param("specialty", "Epidemiology")
                        .param("edu_level", "Residency")
                        .param("category_id", "edu_center_root"));

        long start = System.currentTimeMillis();

        // Query Document 1 using full synonym "Федеральный государственный образовательный стандарт"
        mockMvc.perform(get("/api/v1/documents")
                        .param("q", "Федеральный государственный образовательный стандарт")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name", is("Новый ФГОС ординатура")));

        // Query Document 2 using full synonym "Государственная итоговая аттестация"
        mockMvc.perform(get("/api/v1/documents")
                        .param("q", "Государственная итоговая аттестация")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name", is("Вопросы ГИА 2026")));

        long duration = System.currentTimeMillis() - start;
        assertTrue(duration < 2000, "Synonym search query took longer than 2 seconds");
    }

    @Test
    public void testDocumentVersionLifecycleAndComments() throws Exception {
        // 1. Upload first version
        MockMultipartFile file1 = new MockMultipartFile("file", "v1.pdf", "application/pdf", "v1".getBytes());
        String responseStr = mockMvc.perform(multipart("/api/v1/documents")
                        .file(file1)
                        .param("name", "Инструкция ГЭК")
                        .param("doc_type", "Regulations")
                        .param("specialty", "Epidemiology")
                        .param("edu_level", "Residency")
                        .param("category_id", "edu_center_root"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String docUuid = objectMapper.readTree(responseStr).get("id").asText();

        // 2. Upload version 2
        MockMultipartFile file2 = new MockMultipartFile("file", "v2.pdf", "application/pdf", "v2".getBytes());
        mockMvc.perform(multipart("/api/v1/documents/" + docUuid)
                        .file(file2)
                        .param("name", "Инструкция ГЭК v2")
                        .param("version_comment", "Обновление регламента ГЭК")
                        .with(request -> { request.setMethod("PUT"); return request; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", is(2)))
                .andExpect(jsonPath("$.name", is("Инструкция ГЭК v2")));

        // Verify version history length is 2
        mockMvc.perform(get("/api/v1/documents/" + docUuid + "/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].versionNumber", is(2)))
                .andExpect(jsonPath("$[1].versionNumber", is(1)));

        // 3. Post a comment
        String commentPayload = "{\"text\":\"Прошу проверить регламент ГЭК\"}";
        mockMvc.perform(post("/api/v1/documents/" + docUuid + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text", is("Прошу проверить регламент ГЭК")))
                .andExpect(jsonPath("$.user.username", is("ivan.ivanov@epidem.ru")));

        // Verify comments retrieved
        mockMvc.perform(get("/api/v1/documents/" + docUuid + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].text", is("Прошу проверить регламент ГЭК")));
    }
}
