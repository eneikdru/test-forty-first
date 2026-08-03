package com.eneik.generated.model;

import java.util.Map;

public class ExternalLmsDocument {
    private String externalSystemId;
    private String externalDocId;
    private String title;
    private String filePath;
    private String lmsUrl;
    private Map<String, Object> metadata;

    public ExternalLmsDocument() {}

    public ExternalLmsDocument(String externalSystemId, String externalDocId, String title, String filePath, String lmsUrl, Map<String, Object> metadata) {
        this.externalSystemId = externalSystemId;
        this.externalDocId = externalDocId;
        this.title = title;
        this.filePath = filePath;
        this.lmsUrl = lmsUrl;
        this.metadata = metadata;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getLmsUrl() {
        return lmsUrl;
    }

    public void setLmsUrl(String lmsUrl) {
        this.lmsUrl = lmsUrl;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
