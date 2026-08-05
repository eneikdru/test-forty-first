package com.eneik.generated.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "document_actualization_requests")
public class ActualizationRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "requester_id", nullable = false, length = 100)
    private String requesterId;

    @Column(name = "requester_username", nullable = false, length = 255)
    private String requesterUsername;

    @Column(name = "requester_full_name", length = 255)
    private String requesterFullName;

    @Column(name = "requester_role", length = 100)
    private String requesterRole;

    @Column(name = "reason", nullable = false, length = 4000)
    private String reason;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "PENDING";

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public ActualizationRequest() {}

    public ActualizationRequest(Document document, String requesterId, String requesterUsername, String requesterFullName, String requesterRole, String reason, String status) {
        this.document = document;
        this.requesterId = requesterId;
        this.requesterUsername = requesterUsername;
        this.requesterFullName = requesterFullName;
        this.requesterRole = requesterRole;
        this.reason = reason;
        if (status != null) {
            this.status = status;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public String getRequesterId() {
        return requesterId;
    }

    public void setRequesterId(String requesterId) {
        this.requesterId = requesterId;
    }

    public String getRequesterUsername() {
        return requesterUsername;
    }

    public void setRequesterUsername(String requesterUsername) {
        this.requesterUsername = requesterUsername;
    }

    public String getRequesterFullName() {
        return requesterFullName;
    }

    public void setRequesterFullName(String requesterFullName) {
        this.requesterFullName = requesterFullName;
    }

    public String getRequesterRole() {
        return requesterRole;
    }

    public void setRequesterRole(String requesterRole) {
        this.requesterRole = requesterRole;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
