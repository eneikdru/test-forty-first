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
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class DeliverableReadinessServiceTest {

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
    public void testCalculateReadinessRatioAndAtomicUpdate() {
        String cycleId = "cycle-1";

        // Add 4 deliverables, 1 resolved, 1 done, 1 settled (all terminal/resolved), 1 pending. Ratio: 3/4 = 0.75
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        timeService.setFixedTime(now);

        Deliverable d1 = new Deliverable(cycleId, "resolved");
        d1.setLastUpdated(now);
        Deliverable d2 = new Deliverable(cycleId, "done");
        d2.setLastUpdated(now);
        Deliverable d3 = new Deliverable(cycleId, "settled");
        d3.setLastUpdated(now);
        Deliverable d4 = new Deliverable(cycleId, "pending");
        d4.setLastUpdated(now);

        deliverableRepository.save(d1);
        deliverableRepository.save(d2);
        deliverableRepository.save(d3);
        d4 = deliverableRepository.save(d4);

        entityManager.flush();
        entityManager.clear();

        float initialRatio = deliverableReadinessService.calculateReadinessRatio(cycleId);
        assertEquals(0.75f, initialRatio, 0.001f, "Initial readiness ratio should be 0.75 (3 out of 4 resolved states)");

        // Atomically update the pending deliverable to resolved
        boolean resolved = deliverableReadinessService.resolveItem(d4.getId());
        assertTrue(resolved, "Deliverable should be resolved successfully");

        entityManager.flush();
        entityManager.clear();

        // Attempting to resolve again should fail because it's no longer 'pending'
        boolean resolvedAgain = deliverableReadinessService.resolveItem(d4.getId());
        assertFalse(resolvedAgain, "Deliverable should not be resolved again");

        entityManager.flush();
        entityManager.clear();

        float newRatio = deliverableReadinessService.calculateReadinessRatio(cycleId);
        assertEquals(1.0f, newRatio, 0.001f, "New readiness ratio should be 1.0 after resolving all items");
    }
}
