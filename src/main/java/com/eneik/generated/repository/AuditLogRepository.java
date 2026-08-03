package com.eneik.generated.repository;

import com.eneik.generated.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:userId IS NULL OR a.userId = :userId) AND " +
           "(:categoryId IS NULL OR a.categoryId = :categoryId)")
    Page<AuditLog> searchLogs(
            @Param("userId") String userId,
            @Param("categoryId") String categoryId,
            Pageable pageable
    );
}
