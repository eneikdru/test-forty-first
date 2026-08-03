package com.eneik.generated.service;

import com.eneik.generated.model.EiosExportRecord;
import com.eneik.generated.model.EiosRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class DefaultEiosClient implements EiosClient {
    private static final Logger log = LoggerFactory.getLogger(DefaultEiosClient.class);

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
        log.info("[EiosClient] Fetching EIOS roles...");
        return new ArrayList<>(mockRoles);
    }

    @Override
    public void sendAnalyticsExport(List<EiosExportRecord> records) {
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
