package com.eneik.generated.controller;

import com.eneik.generated.model.AuditLog;
import com.eneik.generated.repository.AuditLogRepository;
import com.eneik.generated.repository.DocumentRepository;
import com.eneik.generated.repository.DocumentVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
public class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    public void setup() {
        documentVersionRepository.deleteAll();
        documentRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    @Test
    public void testFileUploadAndAuditLogRecording() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fgos_epidemiology.pdf", "application/pdf", "FGOS Content".getBytes()
        );

        long startCount = auditLogRepository.count();

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .param("name", "Регламент ФГОС")
                        .param("description", "Учебно-методические материалы по специальности Эпидемиология")
                        .param("doc_type", "Regulations")
                        .param("specialty", "Epidemiology")
                        .param("edu_level", "Residency")
                        .param("category_id", "edu_center_root")
                        .param("tags", "ординатура", "шаблоны")
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name", is("Регламент ФГОС")))
                .andExpect(jsonPath("$.doc_type", is("Regulations")))
                .andExpect(jsonPath("$.specialty", is("Epidemiology")))
                .andExpect(jsonPath("$.edu_level", is("Residency")))
                .andExpect(jsonPath("$.category_id", is("edu_center_root")))
                .andExpect(jsonPath("$.tags", hasItems("ординатура", "шаблоны")))
                .andExpect(jsonPath("$.version", is(1)));

        // Verify audit log was recorded in H2 DB
        List<AuditLog> logs = auditLogRepository.findAll();
        assertEquals(startCount + 1, logs.size());
        AuditLog recordedLog = logs.get(0);
        assertEquals("DOCUMENT_UPLOAD", recordedLog.getAction());
        assertEquals("edu_center_root", recordedLog.getCategoryId());
        assertNotNull(recordedLog.getResourceId());
    }

    @Test
    public void testSynonymFullTextSearch() throws Exception {
        // Upload Document 1: using 'ФГОС' abbreviation
        MockMultipartFile file1 = new MockMultipartFile(
                "file", "doc1.pdf", "application/pdf", "Content 1".getBytes()
        );
        mockMvc.perform(multipart("/api/v1/documents")
                        .file(file1)
                        .param("name", "Рабочая программа ФГОС")
                        .param("doc_type", "Regulations")
                        .param("specialty", "Epidemiology")
                        .param("edu_level", "Residency")
                        .param("category_id", "edu_center_root")
                )
                .andExpect(status().isCreated());

        // Upload Document 2: using expanded term 'Федеральный государственный образовательный стандарт'
        MockMultipartFile file2 = new MockMultipartFile(
                "file", "doc2.pdf", "application/pdf", "Content 2".getBytes()
        );
        mockMvc.perform(multipart("/api/v1/documents")
                        .file(file2)
                        .param("name", "Новый Федеральный государственный образовательный стандарт")
                        .param("doc_type", "Regulations")
                        .param("specialty", "Epidemiology")
                        .param("edu_level", "Residency")
                        .param("category_id", "edu_center_root")
                )
                .andExpect(status().isCreated());

        // Performance check - search with synonyms must be fast (<2s)
        long startTime = System.currentTimeMillis();

        // Querying for 'ФГОС' should return BOTH documents (synonym-aware matching)
        mockMvc.perform(get("/api/v1/documents")
                        .param("q", "ФГОС")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.content[0].name", anyOf(containsString("ФГОС"), containsString("Федеральный"))))
                .andExpect(jsonPath("$.content[1].name", anyOf(containsString("ФГОС"), containsString("Федеральный"))));

        long duration = System.currentTimeMillis() - startTime;
        assertTrue(duration < 2000, "Search query with synonyms took longer than 2 seconds: " + duration + "ms");
    }

    @Test
    public void testSuggestionsAutocomplete() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "Content".getBytes()
        );
        mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .param("name", "ФБУН Правила")
                        .param("doc_type", "Guidelines")
                        .param("specialty", "Epidemiology")
                        .param("edu_level", "Postgraduate")
                        .param("category_id", "edu_academic_reports")
                )
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/documents")
                        .param("q", "ФБУ")
                        .param("suggest", "true")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions", hasItem("ФБУН Правила")));
    }
}
