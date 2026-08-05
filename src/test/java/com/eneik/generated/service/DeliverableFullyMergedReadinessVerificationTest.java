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
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class DeliverableFullyMergedReadinessVerificationTest {

    @Autowired
    private DeliverableReadinessService deliverableReadinessService;

    @Autowired
    private DeliverableRepository deliverableRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TimeService timeService;

    private final String testCycleId = "fully-merged-cycle-verification-1";

    @BeforeEach
    public void setup() {
        deliverableRepository.deleteAll();
        timeService.clearFixedTime();
    }

    @Test
    public void verifyFullyMergedDeliverablesStateTransitionsAndReadiness() {
        // [POL_HORVICH_01_FALSIFICATION_HARNESS] - Ensure fixed, reproducible temporal anchor.
        LocalDateTime fixedNow = LocalDateTime.of(2026, 8, 5, 12, 0, 0);
        timeService.setFixedTime(fixedNow);

        // Given a project where all deliverables are merged (19 out of 19)
        int totalItems = 19;
        for (int i = 0; i < totalItems; i++) {
            Deliverable deliverable = new Deliverable(testCycleId, "merged");
            deliverable.setLastUpdated(fixedNow);
            deliverableRepository.save(deliverable);
        }

        entityManager.flush();
        entityManager.clear();

        // When the readiness state is evaluated
        float readinessRatio = deliverableReadinessService.calculateReadinessRatio(testCycleId);
        boolean isDecompositionComplete = deliverableReadinessService.isDecompositionComplete(testCycleId);
        boolean isStagnationWarningActive = deliverableReadinessService.isStagnationWarningActive(testCycleId);
        boolean isStagnationWarningDismissed = deliverableReadinessService.isStagnationWarningDismissed(testCycleId);

        // Then 100% readiness is reported, decomposition evaluates to complete, and stagnation warning is dismissed
        assertEquals(1.0f, readinessRatio, 0.0001f, "Readiness ratio must be exactly 1.0 (100%) for fully merged deliverables");
        assertTrue(isDecompositionComplete, "Decomposition must be marked complete when all deliverables are merged");
        assertFalse(isStagnationWarningActive, "Stagnation warning must be inactive when all deliverables are merged");
        assertTrue(isStagnationWarningDismissed, "Stagnation warning must be dismissed when all deliverables are merged");
    }

    @Test
    public void verifyIncompleteStateTransitionsWithMixedDeliverables() {
        // [POL_HORVICH_01_FALSIFICATION_HARNESS] - Counterexample to prove we don't return false green under partial completion.
        LocalDateTime fixedNow = LocalDateTime.of(2026, 8, 5, 12, 0, 0);
        timeService.setFixedTime(fixedNow);

        // Given a project with 18 merged items and 1 pending item
        int mergedItemsCount = 18;
        for (int i = 0; i < mergedItemsCount; i++) {
            Deliverable deliverable = new Deliverable(testCycleId, "merged");
            deliverable.setLastUpdated(fixedNow);
            deliverableRepository.save(deliverable);
        }

        Deliverable pendingDeliverable = new Deliverable(testCycleId, "pending");
        pendingDeliverable.setLastUpdated(fixedNow);
        deliverableRepository.save(pendingDeliverable);

        entityManager.flush();
        entityManager.clear();

        // When readiness logic is evaluated
        float readinessRatio = deliverableReadinessService.calculateReadinessRatio(testCycleId);
        boolean isDecompositionComplete = deliverableReadinessService.isDecompositionComplete(testCycleId);
        boolean isStagnationWarningActive = deliverableReadinessService.isStagnationWarningActive(testCycleId);
        boolean isStagnationWarningDismissed = deliverableReadinessService.isStagnationWarningDismissed(testCycleId);

        // Then we assert that it is NOT considered complete or resolved
        float expectedRatio = 18.0f / 19.0f;
        assertEquals(expectedRatio, readinessRatio, 0.001f, "Readiness ratio should correctly reflect 18/19 complete items");
        assertFalse(isDecompositionComplete, "Decomposition should not be marked complete with pending items");
        assertTrue(isStagnationWarningActive, "Stagnation warning should remain active with pending items");
        assertFalse(isStagnationWarningDismissed, "Stagnation warning should not be dismissed with pending items");
    }
}
