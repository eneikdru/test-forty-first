package com.eneik.generated.model;

import java.time.LocalDateTime;

public class EiosExportRecord {
    private String userId;
    private String actionType;
    private String resourceId;
    private String resourceType;
    private LocalDateTime timestamp;
    private String metadata;

    public EiosExportRecord() {}

    public EiosExportRecord(String userId, String actionType, String resourceId, String resourceType, LocalDateTime timestamp, String metadata) {
        this.userId = userId;
        this.actionType = actionType;
        this.resourceId = resourceId;
        this.resourceType = resourceType;
        this.timestamp = timestamp;
        this.metadata = metadata;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
}
