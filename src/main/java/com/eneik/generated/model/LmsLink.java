package com.eneik.generated.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "lms_links")
public class LmsLink {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "external_system_id", nullable = false, length = 100)
    private String externalSystemId;

    @Column(name = "external_doc_id", nullable = false, length = 255)
    private String externalDocId;

    @Column(name = "lms_url", nullable = false, length = 1024)
    private String lmsUrl;

    @Column(name = "remote_metadata", length = 4000)
    private String remoteMetadata;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public LmsLink() {}

    public LmsLink(Document document, String externalSystemId, String externalDocId, String lmsUrl, String remoteMetadata) {
        this.document = document;
        this.externalSystemId = externalSystemId;
        this.externalDocId = externalDocId;
        this.lmsUrl = lmsUrl;
        this.remoteMetadata = remoteMetadata;
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

    public String getExternalSystemId() {
        return externalSystemId;
    }

    public void setExternalSystemId(String externalSystemId) {
        this.externalSystemId = externalSystemId;
    }

    public String getExternalDocId() {
        return externalDocId;
    }

    public void setExternalDocId(String externalDocId) {
        this.externalDocId = externalDocId;
    }

    public String getLmsUrl() {
        return lmsUrl;
    }

    public void setLmsUrl(String lmsUrl) {
        this.lmsUrl = lmsUrl;
    }

    public String getRemoteMetadata() {
        return remoteMetadata;
    }

    public void setRemoteMetadata(String remoteMetadata) {
        this.remoteMetadata = remoteMetadata;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
