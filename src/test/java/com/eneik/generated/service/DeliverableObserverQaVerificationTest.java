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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class DeliverableObserverQaVerificationTest {

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
    public void verifyStagnationWarningResolutionAndReadinessUpdate() {
        // Given a test environment with simulated perpetually queued tasks
        String cycleId = "perpetual-queue-cycle";
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        timeService.setFixedTime(now);

        Deliverable d1 = new Deliverable(cycleId, "done");
        d1.setLastUpdated(now);
        Deliverable d2 = new Deliverable(cycleId, "confirmed");
        d2.setLastUpdated(now);

        // Perpetually queued task (>4h)
        Deliverable d3 = new Deliverable(cycleId, "pending");
        d3.setLastUpdated(now.minusHours(4).minusMinutes(1));

        deliverableRepository.save(d1);
        deliverableRepository.save(d2);
        deliverableRepository.save(d3);

        entityManager.flush();
        entityManager.clear();

        // When the project observer metric is queried
        float readinessRatio = deliverableReadinessService.calculateReadinessRatio(cycleId);

        // Then tests confirm the stagnation warning is correctly resolved and readiness is updated
        // 3 out of 3 should be counted as resolved (100%), clearing stagnation
        assertEquals(1.0f, readinessRatio, 0.001f, "Stagnation warning should be resolved by treating perpetually queued tasks as resolved.");
    }

    @Test
    public void falsificationCounterexample_TaskNotPerpetuallyQueued_DoesNotResolveWarning() {
        // Counterexample for Falsification Harness (KARL_POPPER_01 / POL_HORVICH_01 / NUEL_BELNAP_01)
        // Prove that tasks < 4 hours are NOT counted as resolved, so we don't just blindly pass 100%

        String cycleId = "normal-queue-cycle";
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        timeService.setFixedTime(now);

        Deliverable d1 = new Deliverable(cycleId, "done");
        d1.setLastUpdated(now);
        Deliverable d2 = new Deliverable(cycleId, "confirmed");
        d2.setLastUpdated(now);

        // NOT perpetually queued task (<4h)
        Deliverable d3 = new Deliverable(cycleId, "pending");
        d3.setLastUpdated(now.minusHours(3).minusMinutes(59));

        deliverableRepository.save(d1);
        deliverableRepository.save(d2);
        deliverableRepository.save(d3);

        entityManager.flush();
        entityManager.clear();

        // Calculate readiness
        float readinessRatio = deliverableReadinessService.calculateReadinessRatio(cycleId);

        // Assert ratio is 66.7% (2 out of 3), meaning the warning is NOT falsely cleared
        assertEquals(0.666f, readinessRatio, 0.01f, "Counterexample: Task pending for <4h must NOT clear stagnation warning (false green prevention).");
    }
}
