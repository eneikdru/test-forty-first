package com.eneik.generated.dto;

import java.util.List;

public class DocumentListResponse {
    private List<DocumentResponse> content;
    private long totalElements;
    private int totalPages;
    private int pageNumber;
    private int pageSize;
    private List<String> suggestions;

    public DocumentListResponse() {}

    public DocumentListResponse(List<DocumentResponse> content, long totalElements, int totalPages, int pageNumber, int pageSize, List<String> suggestions) {
        this.content = content;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.suggestions = suggestions;
    }

    public List<DocumentResponse> getContent() {
        return content;
    }

    public void setContent(List<DocumentResponse> content) {
        this.content = content;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }
}
