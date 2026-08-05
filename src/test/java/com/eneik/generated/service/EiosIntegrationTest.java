package com.eneik.generated.service;

import com.eneik.generated.model.*;
import com.eneik.generated.repository.DocumentRepository;
import com.eneik.generated.repository.RoleRepository;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class EiosIntegrationTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private EiosSyncService eiosSyncService;

    @Autowired
    private DefaultEiosClient eiosClient;

    @Autowired
    private LmsIntegrationService lmsIntegrationService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TimeService timeService;

    @Autowired
    private EiosIntegrationScheduler scheduler;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        registry.add("telegram.api.url", () -> "http://localhost:" + wireMockServer.port());
        registry.add("eios.api.url", () -> "http://localhost:" + wireMockServer.port());
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

        // Stub telegram bot notification
        stubFor(post(urlEqualTo("/bot/sendMessage"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));

        notificationService.clearNotifications();
        timeService.clearFixedTime();
    }

    @Test
    public void testDocumentUpdateDispatchesTelegramAndMaxNotifications() {
        // Create initial document
        Document doc = documentService.saveNewDocument(
                "Infectious Diseases Protocol",
                "/files/protocol_v1.pdf",
                "{\"status\": \"draft\"}"
        );
        assertNotNull(doc.getId());

        // Clear pre-existing notifications if any from save
        notificationService.clearNotifications();

        // Update document (this is the process being triggered)
        Document updatedDoc = documentService.updateDocument(
                doc.getId(),
                "Infectious Diseases Protocol",
                "/files/protocol_v2.pdf",
                "{\"status\": \"published\"}"
        );

        // Verify Telegram/Max notification was dispatched
        List<NotificationService.NotificationRecord> dispatches = notificationService.getDispatchedNotifications();

        // We expect at least 2 notifications (one for Telegram, one for Max)
        assertTrue(dispatches.size() >= 2);

        NotificationService.NotificationRecord telegramRecord = dispatches.stream()
                .filter(r -> "Telegram".equals(r.getDestination()) && r.getDocumentId().equals(doc.getId()))
                .findFirst()
                .orElse(null);

        NotificationService.NotificationRecord maxRecord = dispatches.stream()
                .filter(r -> "Max".equals(r.getDestination()) && r.getDocumentId().equals(doc.getId()))
                .findFirst()
                .orElse(null);

        assertNotNull(telegramRecord, "Telegram notification should be dispatched");
        assertNotNull(maxRecord, "Max notification should be dispatched");

        assertEquals(2, telegramRecord.getVersionNumber());
        assertEquals("/files/protocol_v2.pdf", telegramRecord.getFilePath());
        assertTrue(telegramRecord.getMessage().contains("New Version: 2"));

        assertEquals(2, maxRecord.getVersionNumber());
        assertEquals("/files/protocol_v2.pdf", maxRecord.getFilePath());
        assertTrue(maxRecord.getMessage().contains("New Version: 2"));
    }

    @Test
    public void testEiosSyncServiceSynchronizesRoles() {
        // Clear all roles to verify sync cleanly
        roleRepository.deleteAll();

        // Stub roles API via WireMock
        stubFor(get(urlEqualTo("/api/roles"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[" +
                                "  {\"name\": \"Epidemiologist\", \"description\": \"Specialist in epidemics\"}," +
                                "  {\"name\": \"Virologist\", \"description\": \"Virus expert\"}" +
                                "]")));

        // Run sync
        eiosSyncService.syncRoles();

        // Verify roles are present in repository
        List<Role> rolesInDb = roleRepository.findAll();
        assertEquals(2, rolesInDb.size());

        Role epi = roleRepository.findByName("Epidemiologist").orElse(null);
        assertNotNull(epi);
        assertEquals("Specialist in epidemics", epi.getDescription());

        Role viro = roleRepository.findByName("Virologist").orElse(null);
        assertNotNull(viro);
        assertEquals("Virus expert", viro.getDescription());

        // Update a description on EIOS and re-sync
        stubFor(get(urlEqualTo("/api/roles"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[" +
                                "  {\"name\": \"Epidemiologist\", \"description\": \"Epidemiologist with advanced clinical credentials\"}," +
                                "  {\"name\": \"Virologist\", \"description\": \"Virus expert\"}" +
                                "]")));

        eiosSyncService.syncRoles();

        Role updatedEpi = roleRepository.findByName("Epidemiologist").orElse(null);
        assertNotNull(updatedEpi);
        assertEquals("Epidemiologist with advanced clinical credentials", updatedEpi.getDescription());
    }

    @Test
    public void testEiosSyncServiceExportsAnalytics() {
        // Stub analytics API via WireMock
        stubFor(post(urlEqualTo("/api/analytics"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Record some user analytics inside the last 24h window
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "integration-test");

        lmsIntegrationService.recordUserAnalytics(
                "user_99",
                "DOWNLOAD_DOCUMENT",
                "doc_777",
                "DOCUMENT",
                metadata
        );

        // Run analytics export
        eiosSyncService.exportAnalytics();

        // Verify HTTP request payload was delivered correctly using WireMock verifications
        verify(postRequestedFor(urlEqualTo("/api/analytics"))
                .withRequestBody(matchingJsonPath("$[0].userId", equalTo("user_99")))
                .withRequestBody(matchingJsonPath("$[0].actionType", equalTo("DOWNLOAD_DOCUMENT")))
                .withRequestBody(matchingJsonPath("$[0].resourceId", equalTo("doc_777")))
                .withRequestBody(matchingJsonPath("$[0].resourceType", equalTo("DOCUMENT")))
        );
    }

    @Test
    public void testSchedulerTriggeringRunsSuccessfully() {
        // Stub role API and analytics API to prevent scheduler exceptions
        stubFor(get(urlEqualTo("/api/roles"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        stubFor(post(urlEqualTo("/api/analytics"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Verify scheduling triggering doesn't throw exceptions and invokes jobs
        assertDoesNotThrow(() -> scheduler.runEiosSyncAndExport());
    }

    @Test
    public void testDefaultEiosClientThrowsExceptionWhenUrlNotConfigured() {
        DefaultEiosClient clientWithoutUrl = new DefaultEiosClient();
        // Since eiosApiUrl is null, both fetchRoles and sendAnalyticsExport should throw IllegalStateException
        assertThrows(IllegalStateException.class, () -> clientWithoutUrl.fetchRoles());
        assertThrows(IllegalStateException.class, () -> clientWithoutUrl.sendAnalyticsExport(List.of()));
    }
}
