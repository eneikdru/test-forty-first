package com.eneik.generated.repository;

import com.eneik.generated.model.ProjectTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ProjectTaskRepository extends JpaRepository<ProjectTask, Long> {

    Optional<ProjectTask> findByTaskId(String taskId);

    @Modifying
    @Transactional
    @Query("UPDATE ProjectTask t SET t.sessionStatus = :newStatus, t.lastUpdated = :now WHERE t.taskId = :taskId AND t.sessionStatus = :oldStatus")
    int updateSessionStatusAtomically(
            @Param("taskId") String taskId,
            @Param("oldStatus") String oldStatus,
            @Param("newStatus") String newStatus,
            @Param("now") LocalDateTime now
    );
}
