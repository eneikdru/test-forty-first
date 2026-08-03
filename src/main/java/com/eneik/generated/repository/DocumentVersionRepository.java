package com.eneik.generated.repository;

import com.eneik.generated.model.Document;
import com.eneik.generated.model.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {
    List<DocumentVersion> findByDocumentOrderByVersionNumberDesc(Document document);
    Optional<DocumentVersion> findFirstByDocumentOrderByVersionNumberDesc(Document document);
}
