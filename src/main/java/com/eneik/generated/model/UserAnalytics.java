package com.eneik.generated.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_analytics")
public class UserAnalytics {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "action_type", nullable = false, length = 100)
    private String actionType;

    @Column(name = "resource_id", nullable = false, length = 255)
    private String resourceId;

    @Column(name = "resource_type", nullable = false, length = 100)
    private String resourceType;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @Column(length = 4000)
    private String metadata;

    public UserAnalytics() {}

    public UserAnalytics(String userId, String actionType, String resourceId, String resourceType, LocalDateTime timestamp, String metadata) {
        this.userId = userId;
        this.actionType = actionType;
        this.resourceId = resourceId;
        this.resourceType = resourceType;
        this.timestamp = timestamp;
        this.metadata = metadata;
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
