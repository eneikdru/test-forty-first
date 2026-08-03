package com.eneik.generated.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(length = 255)
    private String username;

    @Column(nullable = false, length = 255)
    private String action;

    @Column(name = "resource_id", length = 255)
    private String resourceId;

    @Column(name = "category_id", length = 255)
    private String categoryId;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    public AuditLog() {}

    public AuditLog(String id, String userId, String username, String action, String resourceId, String categoryId, LocalDateTime timestamp) {
        this.id = id;
        this.userId = userId;
        this.username = username;
        this.action = action;
        this.resourceId = resourceId;
        this.categoryId = categoryId;
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

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
