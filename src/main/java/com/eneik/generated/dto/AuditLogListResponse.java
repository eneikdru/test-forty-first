package com.eneik.generated.dto;

import java.util.List;

public class AuditLogListResponse {
    private List<AuditLogEntryResponse> logs;
    private long totalElements;

    public AuditLogListResponse() {}

    public AuditLogListResponse(List<AuditLogEntryResponse> logs, long totalElements) {
        this.logs = logs;
        this.totalElements = totalElements;
    }

    public List<AuditLogEntryResponse> getLogs() {
        return logs;
    }

    public void setLogs(List<AuditLogEntryResponse> logs) {
        this.logs = logs;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }
}
