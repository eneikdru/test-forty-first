package com.eneik.generated.service;

import com.eneik.generated.model.EiosExportRecord;
import com.eneik.generated.model.EiosRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class DefaultEiosClient implements EiosClient {
    private static final Logger log = LoggerFactory.getLogger(DefaultEiosClient.class);

    @Value("${eios.api.url:}")
    private String eiosApiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    private final List<EiosRole> mockRoles = Collections.synchronizedList(new ArrayList<>());
    private final List<EiosExportRecord> exportedRecords = Collections.synchronizedList(new ArrayList<>());

    public DefaultEiosClient() {
        // Initialize with default Eios roles representing our center
        mockRoles.add(new EiosRole("Administrator", "EIOS Admin Role"));
        mockRoles.add(new EiosRole("Content-manager", "EIOS Content Manager Role"));
        mockRoles.add(new EiosRole("Teacher", "EIOS Instructor/Supervisor"));
        mockRoles.add(new EiosRole("Student", "EIOS Postgraduate/Resident/Student"));
    }

    @Override
    public List<EiosRole> fetchRoles() {
        if (eiosApiUrl != null && !eiosApiUrl.trim().isEmpty()) {
            log.info("[EiosClient] Fetching EIOS roles from remote URL: {}", eiosApiUrl);
            try {
                EiosRole[] roles = restTemplate.getForObject(eiosApiUrl + "/api/roles", EiosRole[].class);
                return roles != null ? List.of(roles) : Collections.emptyList();
            } catch (Exception e) {
                log.error("[EiosClient] Failed to fetch EIOS roles from remote URL", e);
                throw new RuntimeException("Failed to fetch EIOS roles from remote", e);
            }
        }
        log.info("[EiosClient] Fetching EIOS roles...");
        return new ArrayList<>(mockRoles);
    }

    @Override
    public void sendAnalyticsExport(List<EiosExportRecord> records) {
        if (eiosApiUrl != null && !eiosApiUrl.trim().isEmpty()) {
            log.info("[EiosClient] Sending EIOS analytics export to remote URL: {}", eiosApiUrl);
            try {
                restTemplate.postForObject(eiosApiUrl + "/api/analytics", records, Void.class);
                log.info("[EiosClient] Successfully sent EIOS analytics export.");
            } catch (Exception e) {
                log.error("[EiosClient] Failed to send EIOS analytics export to remote URL", e);
                throw new RuntimeException("Failed to send EIOS analytics export", e);
            }
        }
        log.info("[EiosClient] Exporting {} analytics records to EIOS...", records.size());
        exportedRecords.clear();
        exportedRecords.addAll(records);
    }

    public List<EiosExportRecord> getExportedRecords() {
        return new ArrayList<>(exportedRecords);
    }

    public void setMockRoles(List<EiosRole> roles) {
        this.mockRoles.clear();
        this.mockRoles.addAll(roles);
    }
}
