package com.eneik.generated.service;

import com.eneik.generated.model.Deliverable;
import com.eneik.generated.repository.DeliverableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DeliverableReadinessVerificationTest {

    @Mock
    private DeliverableRepository deliverableRepository;

    @InjectMocks
    private DeliverableReadinessService deliverableReadinessService;

    private final String cycleId = "test-cycle";

    @Test
    public void testReadinessMetricIncreasesAbove67Percent() {
        // Given the tracking patch is deployed,
        // (State before resolution: 2 terminal states out of 3 total = ~66.7%)
        Deliverable d1 = new Deliverable(cycleId, "done");
        Deliverable d2 = new Deliverable(cycleId, "confirmed");
        Deliverable d3 = new Deliverable(cycleId, "pending");

        List<Deliverable> initialDeliverables = Arrays.asList(d1, d2, d3);
        when(deliverableRepository.findByCycleId(cycleId)).thenReturn(initialDeliverables);

        float initialRatio = deliverableReadinessService.calculateReadinessRatio(cycleId);

        // Assert initial state is ~67%
        assertTrue(initialRatio > 0.66f && initialRatio < 0.68f,
            "Initial ratio must be around 67%");

        // When tests simulate a deliverable resolution,
        d3.setStatus("resolved");
        List<Deliverable> updatedDeliverables = Arrays.asList(d1, d2, d3);
        when(deliverableRepository.findByCycleId(cycleId)).thenReturn(updatedDeliverables);

        float updatedRatio = deliverableReadinessService.calculateReadinessRatio(cycleId);

        // Then the reported readiness metric correctly increases above 67%.
        assertTrue(updatedRatio > 0.67f,
            "Reported readiness metric correctly increases above 67%");

        // Let's assert it is 100% since 3 out of 3 are resolved.
        assertTrue(Math.abs(updatedRatio - 1.0f) < 0.001f,
            "New readiness ratio should be exactly 1.0 (100%)");
    }
}
