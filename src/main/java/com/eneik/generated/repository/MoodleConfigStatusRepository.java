package com.eneik.generated.repository;

import com.eneik.generated.model.MoodleConfigStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;

@Repository
public interface MoodleConfigStatusRepository extends JpaRepository<MoodleConfigStatus, String> {

    @Modifying(clearAutomatically = true)
    @Query("UPDATE MoodleConfigStatus m SET m.status = :toStatus, m.lastConfigured = :now, m.version = m.version + 1 WHERE m.id = :id AND m.status IN :fromStatuses")
    int updateStatusAtomically(
        @Param("id") String id,
        @Param("toStatus") String toStatus,
        @Param("fromStatuses") Collection<String> fromStatuses,
        @Param("now") LocalDateTime now
    );

    @Modifying(clearAutomatically = true)
    @Query("UPDATE MoodleConfigStatus m SET m.status = :toStatus, m.lastConfigured = :now, m.version = m.version + 1 WHERE m.id = :id AND m.status = :fromStatus")
    int updateStatusAtomicallySingle(
        @Param("id") String id,
        @Param("toStatus") String toStatus,
        @Param("fromStatus") String fromStatus,
        @Param("now") LocalDateTime now
    );
}
