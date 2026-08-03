package com.eneik.generated.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "moodle_config_status")
public class MoodleConfigStatus {

    @Id
    @Column(nullable = false, length = 255)
    private String id;

    @Column(name = "last_configured", nullable = false)
    private LocalDateTime lastConfigured;

    @Column(nullable = false, length = 50)
    private String status;

    @Version
    @Column(nullable = false)
    private int version;

    public MoodleConfigStatus() {}

    public MoodleConfigStatus(String id, LocalDateTime lastConfigured, String status, int version) {
        this.id = id;
        this.lastConfigured = lastConfigured;
        this.status = status;
        this.version = version;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public LocalDateTime getLastConfigured() {
        return lastConfigured;
    }

    public void setLastConfigured(LocalDateTime lastConfigured) {
        this.lastConfigured = lastConfigured;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }
}
