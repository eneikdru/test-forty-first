package com.eneik.generated.dto;

public class AuditLogEntryResponse {
    private String id;
    private String userId;
    private String username;
    private String action;
    private String resourceId;
    private String category_id;
    private String timestamp;

    public AuditLogEntryResponse() {}

    public AuditLogEntryResponse(String id, String userId, String username, String action, String resourceId, String category_id, String timestamp) {
        this.id = id;
        this.userId = userId;
        this.username = username;
        this.action = action;
        this.resourceId = resourceId;
        this.category_id = category_id;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getCategory_id() {
        return category_id;
    }

    public void setCategory_id(String category_id) {
        this.category_id = category_id;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
