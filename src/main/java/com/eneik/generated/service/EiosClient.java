package com.eneik.generated.service;

import com.eneik.generated.model.EiosExportRecord;
import com.eneik.generated.model.EiosRole;

import java.util.List;

public interface EiosClient {
    List<EiosRole> fetchRoles();
    void sendAnalyticsExport(List<EiosExportRecord> records);
}
