package com.eneik.generated.model;

import jakarta.persistence.*;

@Entity
@Table(name = "deliverables")
public class Deliverable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_id", nullable = false, length = 100)
    private String cycleId;

    @Column(nullable = false, length = 50)
    private String status;

    public Deliverable() {}

    public Deliverable(String cycleId, String status) {
        this.cycleId = cycleId;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCycleId() {
        return cycleId;
    }

    public void setCycleId(String cycleId) {
        this.cycleId = cycleId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
