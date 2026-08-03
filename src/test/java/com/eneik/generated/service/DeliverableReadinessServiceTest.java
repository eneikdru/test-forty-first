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

    @BeforeEach
    public void setup() {
        deliverableRepository.deleteAll();
    }

    @Test
    public void testCalculateReadinessRatioAndAtomicUpdate() {
        String cycleId = "cycle-1";

        // Add 3 deliverables, 2 resolved, 1 pending. Ratio: 2/3 = ~0.67
        Deliverable d1 = new Deliverable(cycleId, "resolved");
        Deliverable d2 = new Deliverable(cycleId, "resolved");
        Deliverable d3 = new Deliverable(cycleId, "pending");

        deliverableRepository.save(d1);
        deliverableRepository.save(d2);
        d3 = deliverableRepository.save(d3);

        entityManager.flush();
        entityManager.clear();

        float initialRatio = deliverableReadinessService.calculateReadinessRatio(cycleId);
        assertEquals(0.6666f, initialRatio, 0.001f, "Initial readiness ratio should be ~0.67");

        // Atomically update the pending deliverable to resolved
        boolean resolved = deliverableReadinessService.resolveItem(d3.getId());
        assertTrue(resolved, "Deliverable should be resolved successfully");

        entityManager.flush();
        entityManager.clear();

        // Attempting to resolve again should fail because it's no longer 'pending'
        boolean resolvedAgain = deliverableReadinessService.resolveItem(d3.getId());
        assertFalse(resolvedAgain, "Deliverable should not be resolved again");

        entityManager.flush();
        entityManager.clear();

        float newRatio = deliverableReadinessService.calculateReadinessRatio(cycleId);
        assertEquals(1.0f, newRatio, 0.001f, "New readiness ratio should be 1.0 after resolving all items");
    }
}
