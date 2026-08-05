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

        // Default stubs for telegram & eios to prevent unexpected failures
        stubFor(post(urlEqualTo("/bot/sendMessage"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));

        stubFor(post(urlEqualTo("/api/analytics"))
                .willReturn(aResponse()
                        .withStatus(200)));

        stubFor(get(urlEqualTo("/api/roles"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

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

        // Setup mock roles returned by the remote client stub
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

        // Verify analytics are exported
        List<EiosExportRecord> exported = eiosClient.getExportedRecords();
        assertFalse(exported.isEmpty(), "Exported analytics records should not be empty");

        EiosExportRecord record = exported.stream()
                .filter(r -> "user_99".equals(r.getUserId()))
                .findFirst()
                .orElse(null);

        assertNotNull(record);
        assertEquals("DOWNLOAD_DOCUMENT", record.getActionType());
        assertEquals("doc_777", record.getResourceId());
        assertEquals("DOCUMENT", record.getResourceType());
    }

    @Test
    public void testSchedulerTriggeringRunsSuccessfully() {
        // Verify scheduling triggering doesn't throw exceptions and invokes jobs
        assertDoesNotThrow(() -> scheduler.runEiosSyncAndExport());
    }
}
