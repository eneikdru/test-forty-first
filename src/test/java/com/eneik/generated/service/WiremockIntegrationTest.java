package com.eneik.generated.service;

import com.eneik.generated.model.Document;
import com.eneik.generated.model.Role;
import com.eneik.generated.model.EiosExportRecord;
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
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class WiremockIntegrationTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private EiosSyncService eiosSyncService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DefaultEiosClient eiosClient;

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
    }

    @Test
    public void testTelegramBotPayloadValidation() {
        // Stub the Telegram send message endpoint
        stubFor(post(urlEqualTo("/bot/sendMessage"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));

        // Create a new document to trigger version 1
        Document doc = documentService.saveNewDocument(
                "Unified Search Protocol",
                "/files/protocol_v1.pdf",
                "{\"status\":\"draft\"}"
        );

        // Update document, which triggers the notification dispatch with remote API call
        documentService.updateDocument(
                doc.getId(),
                "Unified Search Protocol",
                "/files/protocol_v2.pdf",
                "{\"status\":\"published\"}"
        );

        // Verify that the Telegram endpoint was called and validate payload details
        verify(postRequestedFor(urlEqualTo("/bot/sendMessage"))
                .withRequestBody(matchingJsonPath("$.chat_id", equalTo("@educational_center_channel")))
                .withRequestBody(matchingJsonPath("$.text", containing("Unified Search Protocol")))
                .withRequestBody(matchingJsonPath("$.text", containing("New Version: 2")))
                .withRequestBody(matchingJsonPath("$.text", containing("/files/protocol_v2.pdf")))
        );
    }

    @Test
    public void testEiosSyncRoleUpdates() {
        // Stub the EIOS roles fetch endpoint
        stubFor(get(urlEqualTo("/api/roles"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[" +
                                "  {\"name\": \"Epidemiologist\", \"description\": \"Specialist in epidemiology\"}," +
                                "  {\"name\": \"Virologist\", \"description\": \"Virus expert\"}" +
                                "]")));

        // Clear existing roles to start fresh
        roleRepository.deleteAll();

        // Trigger EIOS sync
        eiosSyncService.syncRoles();

        // Verify local roles correctly reflect the remote state
        List<Role> roles = roleRepository.findAll();
        assertEquals(2, roles.size());

        Role epi = roleRepository.findByName("Epidemiologist").orElse(null);
        assertNotNull(epi);
        assertEquals("Specialist in epidemiology", epi.getDescription());

        Role viro = roleRepository.findByName("Virologist").orElse(null);
        assertNotNull(viro);
        assertEquals("Virus expert", viro.getDescription());
    }

    @Test
    public void testEiosAnalyticsExportTransmission() {
        // Stub the EIOS analytics endpoint
        stubFor(post(urlEqualTo("/api/analytics"))
                .willReturn(aResponse()
                        .withStatus(200)));

        EiosExportRecord record = new EiosExportRecord(
                "user_abc",
                "DOWNLOAD_DOCUMENT",
                "doc_123",
                "DOCUMENT",
                LocalDateTime.now(),
                "{\"foo\":\"bar\"}"
        );

        // Run transmission
        eiosClient.sendAnalyticsExport(List.of(record));

        // Verify that the remote EIOS endpoint was called with correct payload
        verify(postRequestedFor(urlEqualTo("/api/analytics"))
                .withRequestBody(matchingJsonPath("$[0].userId", equalTo("user_abc")))
                .withRequestBody(matchingJsonPath("$[0].actionType", equalTo("DOWNLOAD_DOCUMENT")))
                .withRequestBody(matchingJsonPath("$[0].resourceId", equalTo("doc_123")))
                .withRequestBody(matchingJsonPath("$[0].resourceType", equalTo("DOCUMENT")))
        );
    }
}
