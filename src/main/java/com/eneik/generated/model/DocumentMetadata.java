package com.eneik.generated.model;

import java.util.List;

public class DocumentMetadata {
    private String description;
    private String docType;
    private String specialty;
    private String eduLevel;
    private String categoryId;
    private List<String> tags;

    public DocumentMetadata() {}

    public DocumentMetadata(String description, String docType, String specialty, String eduLevel, String categoryId, List<String> tags) {
        this.description = description;
        this.docType = docType;
        this.specialty = specialty;
        this.eduLevel = eduLevel;
        this.categoryId = categoryId;
        this.tags = tags;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public String getEduLevel() {
        return eduLevel;
    }

    public void setEduLevel(String eduLevel) {
        this.eduLevel = eduLevel;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}
