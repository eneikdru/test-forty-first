package com.eneik.generated.repository;

import com.eneik.generated.model.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {
    List<DocumentVersion> findByDocumentIdOrderByVersionNumberDesc(Long documentId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE DocumentVersion dv SET dv.archived = true WHERE dv.document.id = :documentId AND dv.archived = false")
    int archiveActiveVersions(@Param("documentId") Long documentId);
}
