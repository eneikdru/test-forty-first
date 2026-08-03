package com.eneik.generated.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(nullable = false, length = 255)
    private String username;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "resource_id", nullable = false, length = 255)
    private String resourceId;

    @Column(name = "category_id", length = 100)
    private String categoryId;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public AuditLog() {}

    public AuditLog(String userId, String username, String action, String resourceId, String categoryId, LocalDateTime timestamp) {
        this.userId = userId;
        this.username = username;
        this.action = action;
        this.resourceId = resourceId;
        this.categoryId = categoryId;
        this.timestamp = timestamp;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
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
