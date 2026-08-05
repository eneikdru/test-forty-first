package com.eneik.generated.config;

import com.eneik.generated.model.Document;
import com.eneik.generated.model.DocumentVersion;
import com.eneik.generated.repository.DocumentRepository;
import com.eneik.generated.repository.DocumentVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final ObjectMapper objectMapper;

    public DatabaseSeeder(DocumentRepository documentRepository,
                          DocumentVersionRepository documentVersionRepository,
                          ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        if (documentRepository.count() > 0) {
            return;
        }

        seedDocument(
                "Бюджетный план образовательного центра на 2026 год",
                "Смета расходов и финансовое планирование образовательного центра ФБУН ЦНИИ Эпидемиологии.",
                "Regulations",
                "Other",
                "Residency",
                "edu_budget_finance",
                List.of("бюджет", "финансы", "нормативные акты"),
                1,
                350000L,
                "application/pdf",
                "economist.ivanov@epidem.ru"
        );

        seedDocument(
                "ФГОС ВО по специальности Эпидемиология",
                "Федеральный государственный образовательный стандарт высшего образования по направлению Эпидемиология (ординатура). Обязателен для всех учебных программ кафедры.",
                "Regulations",
                "Epidemiology",
                "Residency",
                "edu_center_root",
                List.of("ординатура", "нормативные акты", "ФГОС"),
                3,
                245000L,
                "application/pdf",
                "ivan.ivanov@epidem.ru"
        );

        seedDocument(
                "Шаблон протокола ГЭК для ГИА",
                "Официальный шаблон протокола заседания государственной экзаменационной комиссии. Применяется для оформления результатов ГИА.",
                "Forms/Templates",
                "Other",
                "Residency",
                "edu_academic_reports",
                List.of("шаблоны", "ГЭК", "ГИА", "ординатура"),
                1,
                125000L,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "elena.petrova@epidem.ru"
        );

        seedDocument(
                "Методические рекомендации по детским инфекционным болезням",
                "Методические материалы по специальности Педиатрия. Включает клинические кейсы, планы занятий и перечень компетенций ФГОС.",
                "Guidelines",
                "Pediatrics",
                "Additional Professional Education",
                "edu_center_root",
                List.of("педиатрия", "инструкции", "рекомендации"),
                2,
                412000L,
                "application/pdf",
                "ivan.ivanov@epidem.ru"
        );

        seedDocument(
                "Вопросы к кандидатскому экзамену по специальности Инфекционные болезни",
                "Перечень теоретических вопросов и практических заданий для подготовки к кандидатскому экзамену по инфекционным болезням аспирантов.",
                "Protocols",
                "Infectious Diseases",
                "Postgraduate",
                "edu_academic_reports",
                List.of("аспирантура", "вопросы к экзаменам", "аттестация"),
                4,
                189000L,
                "application/pdf",
                "sergey.smirnov@epidem.ru"
        );

        seedDocument(
                "Регламент интеграции учебных планов в СЭД ФБУН",
                "Регламент и пошаговые инструкции по выгрузке данных в СЭД образовательного центра ФБУН ЦНИИ Эпидемиологии Роспотребнадзора.",
                "Regulations",
                "Other",
                "Additional Professional Education",
                "edu_staff_workload",
                List.of("ФБУН", "инструкции", "регламент"),
                2,
                310000L,
                "application/pdf",
                "ivan.ivanov@epidem.ru"
        );
    }

    private void seedDocument(String name, String description, String docType, String specialty, String eduLevel,
                              String categoryId, List<String> tags, int version, long fileSize, String fileType, String updatedBy) throws Exception {
        Map<String, Object> metaMap = new LinkedHashMap<>();
        metaMap.put("description", description);
        metaMap.put("doc_type", docType);
        metaMap.put("specialty", specialty);
        metaMap.put("edu_level", eduLevel);
        metaMap.put("category_id", categoryId);
        metaMap.put("tags", tags);
        metaMap.put("fileSize", fileSize);
        metaMap.put("fileType", fileType);
        metaMap.put("updatedBy", updatedBy);

        String metaJson = objectMapper.writeValueAsString(metaMap);
        Document doc = new Document(name, "uploads/mock_file.pdf", metaJson);
        doc = documentRepository.save(doc);

        DocumentVersion ver = new DocumentVersion(doc, version, "uploads/mock_file.pdf", metaJson, false);
        documentVersionRepository.save(ver);
    }
}
