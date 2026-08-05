package com.eneik.generated.service;

import com.eneik.generated.model.EiosExportRecord;
import com.eneik.generated.model.EiosRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Service
public class DefaultEiosClient implements EiosClient {
    private static final Logger log = LoggerFactory.getLogger(DefaultEiosClient.class);

    @Value("${eios.api.url:}")
    private String eiosApiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public List<EiosRole> fetchRoles() {
        if (eiosApiUrl == null || eiosApiUrl.trim().isEmpty()) {
            throw new IllegalStateException("EIOS API URL is not configured. Target URL must be set to prevent fake success.");
        }
        log.info("[EiosClient] Fetching EIOS roles from remote URL: {}", eiosApiUrl);
        try {
            EiosRole[] roles = restTemplate.getForObject(eiosApiUrl + "/api/roles", EiosRole[].class);
            return roles != null ? List.of(roles) : Collections.emptyList();
        } catch (Exception e) {
            log.error("[EiosClient] Failed to fetch EIOS roles from remote URL", e);
            throw new RuntimeException("Failed to fetch EIOS roles from remote", e);
        }
    }

    @Override
    public void sendAnalyticsExport(List<EiosExportRecord> records) {
        if (eiosApiUrl == null || eiosApiUrl.trim().isEmpty()) {
            throw new IllegalStateException("EIOS API URL is not configured. Target URL must be set to prevent fake success.");
        }
        if (records == null) {
            log.warn("[EiosClient] EIOS analytics records list is null, ignoring export.");
            return;
        }
        log.info("[EiosClient] Sending {} EIOS analytics export records to remote URL: {}", records.size(), eiosApiUrl);
        try {
            restTemplate.postForObject(eiosApiUrl + "/api/analytics", records, Void.class);
            log.info("[EiosClient] Successfully sent {} EIOS analytics export records.", records.size());
        } catch (Exception e) {
            log.error("[EiosClient] Failed to send EIOS analytics export to remote URL: {}", eiosApiUrl, e);
            throw new RuntimeException("Failed to send EIOS analytics export", e);
        }
    }
}
