package com.eneik.generated.service;

import com.eneik.generated.model.EiosExportRecord;
import com.eneik.generated.model.EiosRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DefaultEiosClientTest {

    @InjectMocks
    private DefaultEiosClient defaultEiosClient;

    @Mock
    private RestTemplate restTemplate;

    @BeforeEach
    public void setUp() {
        // Set the private field restTemplate
        ReflectionTestUtils.setField(defaultEiosClient, "restTemplate", restTemplate);
        // Set the eios.api.url value
        ReflectionTestUtils.setField(defaultEiosClient, "eiosApiUrl", "http://mock-eios-url.com");
    }

    @Test
    public void testFetchRolesSuccess() {
        EiosRole[] mockRoles = new EiosRole[] {
                new EiosRole("Role1", "Desc1"),
                new EiosRole("Role2", "Desc2")
        };

        when(restTemplate.getForObject("http://mock-eios-url.com/api/roles", EiosRole[].class))
                .thenReturn(mockRoles);

        List<EiosRole> result = defaultEiosClient.fetchRoles();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Role1", result.get(0).getName());
        assertEquals("Desc1", result.get(0).getDescription());
        assertEquals("Role2", result.get(1).getName());
        assertEquals("Desc2", result.get(1).getDescription());

        verify(restTemplate, times(1)).getForObject("http://mock-eios-url.com/api/roles", EiosRole[].class);
    }

    @Test
    public void testFetchRolesNullResponseReturnsEmptyList() {
        when(restTemplate.getForObject("http://mock-eios-url.com/api/roles", EiosRole[].class))
                .thenReturn(null);

        List<EiosRole> result = defaultEiosClient.fetchRoles();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testFetchRolesThrowsExceptionOnError() {
        when(restTemplate.getForObject("http://mock-eios-url.com/api/roles", EiosRole[].class))
                .thenThrow(new RuntimeException("Connection timed out"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> defaultEiosClient.fetchRoles());
        assertTrue(ex.getMessage().contains("Failed to fetch EIOS roles from remote"));
    }

    @Test
    public void testFetchRolesThrowsIllegalStateExceptionWhenUrlNotConfigured() {
        ReflectionTestUtils.setField(defaultEiosClient, "eiosApiUrl", "");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> defaultEiosClient.fetchRoles());
        assertTrue(ex.getMessage().contains("EIOS API URL is not configured"));
    }

    @Test
    public void testSendAnalyticsExportSuccess() {
        LocalDateTime testTime = LocalDateTime.of(2026, 8, 5, 12, 0, 0);
        List<EiosExportRecord> records = List.of(
                new EiosExportRecord("user1", "VIEW", "doc1", "DOCUMENT", testTime, "{}")
        );

        defaultEiosClient.sendAnalyticsExport(records);

        verify(restTemplate, times(1)).postForObject("http://mock-eios-url.com/api/analytics", records, Void.class);
    }

    @Test
    public void testSendAnalyticsExportNullRecordsDoesNothing() {
        defaultEiosClient.sendAnalyticsExport(null);

        verifyNoInteractions(restTemplate);
    }

    @Test
    public void testSendAnalyticsExportThrowsExceptionOnError() {
        LocalDateTime testTime = LocalDateTime.of(2026, 8, 5, 12, 0, 0);
        List<EiosExportRecord> records = List.of(
                new EiosExportRecord("user1", "VIEW", "doc1", "DOCUMENT", testTime, "{}")
        );

        when(restTemplate.postForObject(eq("http://mock-eios-url.com/api/analytics"), any(), eq(Void.class)))
                .thenThrow(new RuntimeException("Internal Server Error"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> defaultEiosClient.sendAnalyticsExport(records));
        assertTrue(ex.getMessage().contains("Failed to send EIOS analytics export"));
    }

    @Test
    public void testSendAnalyticsExportThrowsIllegalStateExceptionWhenUrlNotConfigured() {
        ReflectionTestUtils.setField(defaultEiosClient, "eiosApiUrl", "   ");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> defaultEiosClient.sendAnalyticsExport(new ArrayList<>()));
        assertTrue(ex.getMessage().contains("EIOS API URL is not configured"));
    }
}
