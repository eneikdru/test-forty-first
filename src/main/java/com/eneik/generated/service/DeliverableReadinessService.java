package com.eneik.generated.service;

import com.eneik.generated.model.Deliverable;
import com.eneik.generated.repository.DeliverableRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DeliverableReadinessService {

    private final DeliverableRepository deliverableRepository;
    private final TimeService timeService;

    public DeliverableReadinessService(DeliverableRepository deliverableRepository, TimeService timeService) {
        this.deliverableRepository = deliverableRepository;
        this.timeService = timeService;
    }

    @Transactional
    public boolean resolveItem(Long deliverableId) {
        int updatedCount = deliverableRepository.updateStatusAtomically(deliverableId, "pending", "resolved", timeService.getCurrentTime());
        return updatedCount > 0;
    }

    @Transactional
    public void addItem(String cycleId, String status) {
        Deliverable deliverable = new Deliverable(cycleId, status);
        deliverable.setLastUpdated(timeService.getCurrentTime());
        deliverableRepository.save(deliverable);
    }

    @Transactional(readOnly = true)
    public float calculateReadinessRatio(String cycleId) {
        List<Deliverable> cycleDeliverables = deliverableRepository.findByCycleId(cycleId);

        if (cycleDeliverables.isEmpty()) {
            return 0.0f;
        }

        long resolvedCount = cycleDeliverables.stream()
                .filter(d -> !"pending".equals(d.getStatus()))
                .count();

        return (float) resolvedCount / cycleDeliverables.size();
    }
}
