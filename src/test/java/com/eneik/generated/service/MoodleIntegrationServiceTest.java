package com.eneik.generated.service;

import com.eneik.generated.model.AuditLog;
import com.eneik.generated.repository.AuditLogRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class MoodleIntegrationServiceTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private MoodleConfigurationService moodleConfigurationService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        registry.add("moodle.api.url", () -> "http://localhost:" + wireMockServer.port());
        registry.add("moodle.api.token", () -> "moodle_test_token_999");
    }

    @AfterAll
    static void stopServer() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    public void setup() {
        WireMock.configureFor("localhost", wireMockServer.port());
        wireMockServer.resetAll();
    }

    @Test
    public void testMoodleDeterministicConfiguration() {
        // Stub category creation endpoint
        stubFor(post(urlEqualTo("/webservice/rest/server.php"))
                .withRequestBody(containing("core_course_create_categories"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[" +
                                "  {\"id\": 501, \"name\": \"Budget\", \"idnumber\": \"edu_budget_finance\"}," +
                                "  {\"id\": 502, \"name\": \"Workload\", \"idnumber\": \"edu_staff_workload\"}," +
                                "  {\"id\": 503, \"name\": \"Scholarship\", \"idnumber\": \"edu_scholarships\"}" +
                                "]")));

        // Stub capability override permissions endpoint
        stubFor(post(urlEqualTo("/webservice/rest/server.php"))
                .withRequestBody(containing("core_role_override_permissions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\": \"ok\"}")));

        // Execute deterministic setup
        Map<String, Object> result = moodleConfigurationService.configureMoodle("user_admin_001", "admin.test@epidem.ru");

        // Assert response values
        assertEquals("SUCCESS", result.get("status"));
        assertEquals(4, result.get("configuredRolesCount"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> created = (List<Map<String, Object>>) result.get("createdCategories");
        assertNotNull(created);
        assertEquals(3, created.size());
        assertEquals("Budget", created.get(0).get("name"));
        assertEquals("Workload", created.get(1).get("name"));
        assertEquals("Scholarship", created.get(2).get("name"));

        // Verify that the Moodle category creation request was made with exact parameters
        verify(postRequestedFor(urlEqualTo("/webservice/rest/server.php"))
                .withRequestBody(containing("wsfunction=core_course_create_categories"))
                .withRequestBody(containing("wstoken=moodle_test_token_999"))
                .withRequestBody(containing("categories%5B0%5D%5Bname%5D=Budget"))
                .withRequestBody(containing("categories%5B0%5D%5Bidnumber%5D=edu_budget_finance"))
                .withRequestBody(containing("categories%5B1%5D%5Bname%5D=Workload"))
                .withRequestBody(containing("categories%5B1%5D%5Bidnumber%5D=edu_staff_workload"))
                .withRequestBody(containing("categories%5B2%5D%5Bname%5D=Scholarship"))
                .withRequestBody(containing("categories%5B2%5D%5Bidnumber%5D=edu_scholarships"))
        );

        // Verify capability override requests were sent for strict permissions for each role
        verify(postRequestedFor(urlEqualTo("/webservice/rest/server.php"))
                .withRequestBody(containing("wsfunction=core_role_override_permissions"))
                .withRequestBody(containing("role=Administrator"))
                .withRequestBody(containing("permissions%5B0%5D%5Bcapability%5D=moodle%2Fcategory%3Amanage"))
        );

        verify(postRequestedFor(urlEqualTo("/webservice/rest/server.php"))
                .withRequestBody(containing("wsfunction=core_role_override_permissions"))
                .withRequestBody(containing("role=Content-manager"))
                .withRequestBody(containing("permissions%5B3%5D%5Bcapability%5D=moodle%2Fcourse%3Adelete"))
                .withRequestBody(containing("permissions%5B3%5D%5Bpermission%5D=prevent"))
        );

        verify(postRequestedFor(urlEqualTo("/webservice/rest/server.php"))
                .withRequestBody(containing("wsfunction=core_role_override_permissions"))
                .withRequestBody(containing("role=Teacher"))
                .withRequestBody(containing("permissions%5B1%5D%5Bcapability%5D=moodle%2Fcourse%3Acreate"))
                .withRequestBody(containing("permissions%5B1%5D%5Bpermission%5D=prevent"))
        );

        verify(postRequestedFor(urlEqualTo("/webservice/rest/server.php"))
                .withRequestBody(containing("wsfunction=core_role_override_permissions"))
                .withRequestBody(containing("role=Student"))
                .withRequestBody(containing("permissions%5B2%5D%5Bcapability%5D=moodle%2Fcourse%3Aupdate"))
                .withRequestBody(containing("permissions%5B2%5D%5Bpermission%5D=prevent"))
        );

        // Verify audit logging entry
        List<AuditLog> logs = auditLogRepository.findAll();
        AuditLog configLog = logs.stream()
                .filter(log -> "MOODLE_CONFIG".equals(log.getAction()))
                .findFirst()
                .orElse(null);

        assertNotNull(configLog, "Audit log entry for Moodle configuration should be saved.");
        assertEquals("user_admin_001", configLog.getUserId());
        assertEquals("admin.test@epidem.ru", configLog.getUsername());
        assertEquals("edu_center_root", configLog.getCategoryId());
    }
}
