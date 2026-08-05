package com.eneik.generated.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "project_tasks")
public class ProjectTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false, unique = true, length = 100)
    private String taskId;

    @Column(name = "pr_number")
    private Integer prNumber;

    @Column(name = "session_status", nullable = false, length = 100)
    private String sessionStatus;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    public ProjectTask() {}

    public ProjectTask(String taskId, Integer prNumber, String sessionStatus) {
        this.taskId = taskId;
        this.prNumber = prNumber;
        this.sessionStatus = sessionStatus;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Integer getPrNumber() {
        return prNumber;
    }

    public void setPrNumber(Integer prNumber) {
        this.prNumber = prNumber;
    }

    public String getSessionStatus() {
        return sessionStatus;
    }

    public void setSessionStatus(String sessionStatus) {
        this.sessionStatus = sessionStatus;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
