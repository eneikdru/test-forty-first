package com.eneik.generated.service;

import com.eneik.generated.model.Deliverable;
import com.eneik.generated.repository.DeliverableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class DeliverableStagnationIntegrationTest {

    @Autowired
    private DeliverableReadinessService deliverableReadinessService;

    @Autowired
    private DeliverableRepository deliverableRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TimeService timeService;

    @BeforeEach
    public void setup() {
        deliverableRepository.deleteAll();
        timeService.clearFixedTime();
    }

    @Test
    public void testReadinessRatioClearsStagnationWarning() {
        String cycleId = "stagnation-cycle";

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        timeService.setFixedTime(now);

        // Setup the ~67% stagnation warning state with 2 resolved items and 1 pending item
        Deliverable d1 = new Deliverable(cycleId, "done");
        d1.setLastUpdated(now);
        Deliverable d2 = new Deliverable(cycleId, "confirmed");
        d2.setLastUpdated(now);
        Deliverable d3 = new Deliverable(cycleId, "pending");
        d3.setLastUpdated(now);

        deliverableRepository.save(d1);
        deliverableRepository.save(d2);
        d3 = deliverableRepository.save(d3);

        entityManager.flush();
        entityManager.clear();

        // Calculate initial ratio
        float initialRatio = deliverableReadinessService.calculateReadinessRatio(cycleId);

        // Assert initial state is ~67% (2 out of 3)
        assertEquals(0.666f, initialRatio, 0.01f, "Initial readiness ratio should be approximately 67%");

        // Resolve the pending item
        boolean resolved = deliverableReadinessService.resolveItem(d3.getId());
        assertTrue(resolved, "Pending item should be successfully resolved");

        entityManager.flush();
        entityManager.clear();

        // Calculate updated ratio
        float updatedRatio = deliverableReadinessService.calculateReadinessRatio(cycleId);

        // Assert the ratio is exactly 1.0, meaning the stagnation warning state is cleared
        assertEquals(1.0f, updatedRatio, 0.001f, "New readiness ratio should be exactly 1.0 (100%), clearing stagnation warning");
    }

    @Test
    public void testTaskStuckForOver4HoursIsCountedAsResolved() {
        String cycleId = "stuck-cycle";

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        timeService.setFixedTime(now);

        Deliverable d1 = new Deliverable(cycleId, "done");
        d1.setLastUpdated(now);

        // Pending task, stuck for >4h
        Deliverable d2 = new Deliverable(cycleId, "pending");
        d2.setLastUpdated(now.minusHours(5));

        deliverableRepository.save(d1);
        deliverableRepository.save(d2);

        entityManager.flush();
        entityManager.clear();

        // Calculate ratio. Even though d2 is "pending", it should be counted as resolved because it's stuck > 4 hours.
        float ratio = deliverableReadinessService.calculateReadinessRatio(cycleId);

        assertEquals(1.0f, ratio, 0.001f, "Task stuck for >4h should be considered resolved, returning 100% readiness");
    }
}
