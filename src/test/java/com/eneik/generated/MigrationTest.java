package com.eneik.generated;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=true"
})
public class MigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void testMigration() {
        Integer tagsCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM financial_document_tags", Integer.class);
        assertEquals(3, tagsCount, "There should be 3 custom fields for financial document tags created in Moodle");

        Integer termsCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM financial_terms", Integer.class);
        assertEquals(3, termsCount, "There should be 3 financial terms populated from glossary import");

        Integer budgetsCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM financial_document_tags WHERE name = 'Budget'", Integer.class);
        assertEquals(1, budgetsCount);

        Integer roiCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM financial_terms WHERE term = 'ROI'", Integer.class);
        assertEquals(1, roiCount);
    }
}
