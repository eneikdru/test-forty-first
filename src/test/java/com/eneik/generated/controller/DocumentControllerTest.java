package com.eneik.generated.controller;

import java.util.UUID;
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

    @Autowired
    private com.eneik.generated.service.DocumentService documentService;

    @Test
    public void testExportPdfSuccessAndAuditLogged() throws Exception {
        // 1. Upload a document
        MockMultipartFile file = new MockMultipartFile(
                "file", "fgos_test.pdf", "application/pdf", "Mock PDF content".getBytes()
        );

        String responseStr = mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .param("name", "ФГОС Эпидемиология")
                        .param("description", "Учебные материалы по ФГОС")
                        .param("doc_type", "Regulations")
                        .param("specialty", "Epidemiology")
                        .param("edu_level", "Residency")
                        .param("category_id", "edu_center_root"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> docObj = objectMapper.readValue(responseStr, Map.class);
        String uuid = (String) docObj.get("id");

        // Clear audit log to isolate export log check
        auditLogRepository.deleteAll();

        // 2. Perform export to PDF
        mockMvc.perform(get("/api/v1/documents/" + uuid + "/export")
                        .param("format", "pdf")
                        .header("Authorization", "Bearer ivan.ivanov@epidem.ru"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename=\"document.pdf\"")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=UTF-8''%D0%A4%D0%93%D0%9E%D0%A1%20%D0%AD%D0%BF%D0%B8%D0%B4%D0%B5%D0%BC%D0%B8%D0%BE%D0%BB%D0%BE%D0%B3%D0%B8%D1%8F.pdf")))
                .andExpect(content().bytes(documentService.exportToPdf(documentRepository.findAll().get(0))));

        // 3. Verify Audit Log is recorded for export
        List<AuditLog> logs = auditLogRepository.findAll();
        assertEquals(1, logs.size());
        AuditLog log = logs.get(0);
        assertEquals("ivan.ivanov@epidem.ru", log.getUsername());
        assertEquals("DOCUMENT_EXPORT", log.getAction());
        assertEquals(uuid, log.getResourceId());
    }

    @Test
    public void testExportDocxSuccess() throws Exception {
        // 1. Upload a document
        MockMultipartFile file = new MockMultipartFile(
                "file", "docx_test.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Mock DOCX content".getBytes()
        );

        String responseStr = mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .param("name", "Учебный план")
                        .param("description", "Описание")
                        .param("doc_type", "Curriculum")
                        .param("specialty", "Other")
                        .param("edu_level", "Residency")
                        .param("category_id", "edu_center_root"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> docObj = objectMapper.readValue(responseStr, Map.class);
        String uuid = (String) docObj.get("id");

        // 2. Perform export to DOCX
        mockMvc.perform(get("/api/v1/documents/" + uuid + "/export")
                        .param("format", "docx")
                        .header("Authorization", "Bearer ivan.ivanov@epidem.ru"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename=\"document.docx\"")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=UTF-8''%D0%A3%D1%87%D0%B5%D0%B1%D0%BD%D1%8B%D0%B9%20%D0%BF%D0%BB%D0%B0%D0%BD.docx")));
    }

    @Test
    public void testExportAccessRestrictions() throws Exception {
        // 1. Upload a financial document (edu_budget_finance)
        MockMultipartFile file = new MockMultipartFile(
                "file", "budget.pdf", "application/pdf", "Budget content".getBytes()
        );

        String responseStr = mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .param("name", "Финансовый отчет")
                        .param("description", "Бюджетный план")
                        .param("doc_type", "Regulations")
                        .param("specialty", "Other")
                        .param("edu_level", "Residency")
                        .param("category_id", "edu_budget_finance"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> docObj = objectMapper.readValue(responseStr, Map.class);
        String uuid = (String) docObj.get("id");

        // 2. Export by Student -> Forbidden (403)
        mockMvc.perform(get("/api/v1/documents/" + uuid + "/export")
                        .param("format", "pdf")
                        .header("Authorization", "Bearer student.petrov@epidem.ru"))
                .andExpect(status().isForbidden());

        // 3. Export by Economist -> Allowed (200)
        mockMvc.perform(get("/api/v1/documents/" + uuid + "/export")
                        .param("format", "pdf")
                        .header("Authorization", "Bearer economist.ivanov@epidem.ru"))
                .andExpect(status().isOk());

        // 4. Export by Administrator -> Allowed (200)
        mockMvc.perform(get("/api/v1/documents/" + uuid + "/export")
                        .param("format", "pdf")
                        .header("Authorization", "Bearer ivan.ivanov@epidem.ru"))
                .andExpect(status().isOk());
    }

    @Test
    public void testExportInvalidFormatAndNotFound() throws Exception {
        // Try invalid format
        mockMvc.perform(get("/api/v1/documents/non-existent-uuid/export")
                        .param("format", "invalid_format")
                        .header("Authorization", "Bearer ivan.ivanov@epidem.ru"))
                .andExpect(status().isBadRequest());

        // Try valid format but non-existent document
        String nonExistentUuid = UUID.randomUUID().toString();
        mockMvc.perform(get("/api/v1/documents/" + nonExistentUuid + "/export")
                        .param("format", "pdf")
                        .header("Authorization", "Bearer ivan.ivanov@epidem.ru"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testExportSpecificEndpoints() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fgos_test.pdf", "application/pdf", "Mock PDF content".getBytes()
        );

        String responseStr = mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .param("name", "ФГОС")
                        .param("description", "Учебные материалы по ФГОС")
                        .param("doc_type", "Regulations")
                        .param("specialty", "Epidemiology")
                        .param("edu_level", "Residency")
                        .param("category_id", "edu_center_root"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> docObj = objectMapper.readValue(responseStr, Map.class);
        String uuid = (String) docObj.get("id");

        // Test specific PDF endpoint
        mockMvc.perform(get("/api/v1/documents/" + uuid + "/export/pdf")
                        .header("Authorization", "Bearer ivan.ivanov@epidem.ru"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));

        // Test specific DOCX endpoint
        mockMvc.perform(get("/api/v1/documents/" + uuid + "/export/docx")
                        .header("Authorization", "Bearer ivan.ivanov@epidem.ru"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }
}
