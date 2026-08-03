package com.eneik.generated.repository;

import com.eneik.generated.model.Deliverable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface DeliverableRepository extends JpaRepository<Deliverable, Long> {

    List<Deliverable> findByCycleId(String cycleId);

    @Modifying
    @Transactional
    @Query("UPDATE Deliverable d SET d.status = :newStatus, d.lastUpdated = :now WHERE d.id = :id AND d.status = :oldStatus")
    int updateStatusAtomically(@Param("id") Long id, @Param("oldStatus") String oldStatus, @Param("newStatus") String newStatus, @Param("now") java.time.LocalDateTime now);
}
