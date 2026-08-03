package com.eneik.generated.service;

import com.eneik.generated.model.MoodleConfigStatus;
import com.eneik.generated.repository.MoodleConfigStatusRepository;
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

import java.time.LocalDateTime;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class MoodleConfigServiceTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private MoodleConfigService moodleConfigService;

    @Autowired
    private MoodleConfigStatusRepository moodleConfigStatusRepository;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        registry.add("moodle.api.url", () -> "http://localhost:" + wireMockServer.port() + "/webservice/rest/server.php");
        registry.add("moodle.api.token", () -> "mock-moodle-token-123");
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

        // Ensure database state is reset before each test
        moodleConfigStatusRepository.deleteAll();
        MoodleConfigStatus initialStatus = new MoodleConfigStatus("MOODLE_CONFIG", LocalDateTime.now(), "PENDING", 1);
        moodleConfigStatusRepository.saveAndFlush(initialStatus);
    }

    @Test
    public void testMoodleConfigurationCreatesCategoriesAndAssignsRoles() {
        // Stub core_course_get_categories to return empty list (no existing categories)
        stubFor(get(urlPathEqualTo("/webservice/rest/server.php"))
                .withQueryParam("wsfunction", equalTo("core_course_get_categories"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        // Stub category creations
        // For edu_center_root
        stubFor(post(urlPathEqualTo("/webservice/rest/server.php"))
                .withQueryParam("wsfunction", equalTo("core_course_create_categories"))
                .withRequestBody(containing("categories%5B0%5D%5Bidnumber%5D=edu_center_root"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\": 100, \"name\": \"Education Center Root\"}]")));

        // For edu_budget_finance
        stubFor(post(urlPathEqualTo("/webservice/rest/server.php"))
                .withQueryParam("wsfunction", equalTo("core_course_create_categories"))
                .withRequestBody(containing("categories%5B0%5D%5Bidnumber%5D=edu_budget_finance"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\": 201, \"name\": \"Budget\"}]")));

        // For edu_staff_workload
        stubFor(post(urlPathEqualTo("/webservice/rest/server.php"))
                .withQueryParam("wsfunction", equalTo("core_course_create_categories"))
                .withRequestBody(containing("categories%5B0%5D%5Bidnumber%5D=edu_staff_workload"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\": 202, \"name\": \"Workload\"}]")));

        // For edu_scholarships
        stubFor(post(urlPathEqualTo("/webservice/rest/server.php"))
                .withQueryParam("wsfunction", equalTo("core_course_create_categories"))
                .withRequestBody(containing("categories%5B0%5D%5Bidnumber%5D=edu_scholarships"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\": 203, \"name\": \"Scholarships\"}]")));

        // For edu_academic_reports
        stubFor(post(urlPathEqualTo("/webservice/rest/server.php"))
                .withQueryParam("wsfunction", equalTo("core_course_create_categories"))
                .withRequestBody(containing("categories%5B0%5D%5Bidnumber%5D=edu_academic_reports"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\": 204, \"name\": \"Reports\"}]")));

        // Stub role assignments
        stubFor(post(urlPathEqualTo("/webservice/rest/server.php"))
                .withQueryParam("wsfunction", equalTo("core_role_assign"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        // Trigger Moodle configuration
        moodleConfigService.configureMoodle();

        // Verify status in DB is COMPLETED
        MoodleConfigStatus status = moodleConfigStatusRepository.findById("MOODLE_CONFIG").orElse(null);
        assertNotNull(status);
        assertEquals("COMPLETED", status.getStatus());

        // Verify calls to mock Moodle REST API
        verify(postRequestedFor(urlPathEqualTo("/webservice/rest/server.php"))
                .withQueryParam("wsfunction", equalTo("core_course_create_categories"))
                .withQueryParam("wstoken", equalTo("mock-moodle-token-123"))
                .withRequestBody(containing("categories%5B0%5D%5Bidnumber%5D=edu_center_root")));

        verify(postRequestedFor(urlPathEqualTo("/webservice/rest/server.php"))
                .withQueryParam("wsfunction", equalTo("core_course_create_categories"))
                .withRequestBody(containing("categories%5B0%5D%5Bparent%5D=100"))
                .withRequestBody(containing("categories%5B0%5D%5Bidnumber%5D=edu_budget_finance")));

        verify(postRequestedFor(urlPathEqualTo("/webservice/rest/server.php"))
                .withQueryParam("wsfunction", equalTo("core_role_assign"))
                .withRequestBody(containing("assignments%5B0%5D%5Broleid%5D=1"))
                .withRequestBody(containing("assignments%5B0%5D%5Buserid%5D=10"))
                .withRequestBody(containing("assignments%5B0%5D%5Bcontextid%5D=201")));
    }

    @Test
    public void testMoodleConfigurationIdempotentWhenCategoriesAlreadyExist() {
        // Setup initial status to PENDING
        MoodleConfigStatus currentStatus = moodleConfigStatusRepository.findById("MOODLE_CONFIG").orElseThrow();
        currentStatus.setStatus("PENDING");
        moodleConfigStatusRepository.saveAndFlush(currentStatus);

        // Stub core_course_get_categories to return already existing categories
        stubFor(get(urlPathEqualTo("/webservice/rest/server.php"))
                .withQueryParam("wsfunction", equalTo("core_course_get_categories"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[" +
                                "  {\"id\": 100, \"idnumber\": \"edu_center_root\", \"name\": \"Education Center Root\"}," +
                                "  {\"id\": 201, \"idnumber\": \"edu_budget_finance\", \"name\": \"Budget\"}," +
                                "  {\"id\": 202, \"idnumber\": \"edu_staff_workload\", \"name\": \"Workload\"}," +
                                "  {\"id\": 203, \"idnumber\": \"edu_scholarships\", \"name\": \"Scholarships\"}," +
                                "  {\"id\": 204, \"idnumber\": \"edu_academic_reports\", \"name\": \"Reports\"}" +
                                "]")));

        // Stub role assignments
        stubFor(post(urlPathEqualTo("/webservice/rest/server.php"))
                .withQueryParam("wsfunction", equalTo("core_role_assign"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        // Trigger Moodle configuration
        moodleConfigService.configureMoodle();

        // Verify status in DB is COMPLETED
        MoodleConfigStatus status = moodleConfigStatusRepository.findById("MOODLE_CONFIG").orElse(null);
        assertNotNull(status);
        assertEquals("COMPLETED", status.getStatus());

        // Verify that NO POST request was sent to core_course_create_categories (since categories exist)
        verify(0, postRequestedFor(urlPathEqualTo("/webservice/rest/server.php"))
                .withQueryParam("wsfunction", equalTo("core_course_create_categories")));

        // But verify that role assignments were still performed correctly
        verify(postRequestedFor(urlPathEqualTo("/webservice/rest/server.php"))
                .withQueryParam("wsfunction", equalTo("core_role_assign"))
                .withRequestBody(containing("assignments%5B0%5D%5Broleid%5D=1"))
                .withRequestBody(containing("assignments%5B0%5D%5Buserid%5D=10"))
                .withRequestBody(containing("assignments%5B0%5D%5Bcontextid%5D=201")));
    }

    @Test
    public void testConcurrentConfigurationTriggersFailSafely() {
        // Force the DB state to CONFIGURING to simulate another thread already running the config
        MoodleConfigStatus currentStatus = moodleConfigStatusRepository.findById("MOODLE_CONFIG").orElseThrow();
        currentStatus.setStatus("CONFIGURING");
        moodleConfigStatusRepository.saveAndFlush(currentStatus);

        // Attempting to run configuration now should throw IllegalStateException
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> moodleConfigService.configureMoodle());
        assertTrue(ex.getMessage().contains("Another Moodle configuration process is already in progress"));

        // Verify state is still CONFIGURING
        MoodleConfigStatus status = moodleConfigStatusRepository.findById("MOODLE_CONFIG").orElse(null);
        assertNotNull(status);
        assertEquals("CONFIGURING", status.getStatus());
    }
}
