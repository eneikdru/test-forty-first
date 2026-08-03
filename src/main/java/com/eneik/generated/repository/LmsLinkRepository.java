package com.eneik.generated.repository;

import com.eneik.generated.model.LmsLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LmsLinkRepository extends JpaRepository<LmsLink, Long> {
    List<LmsLink> findByDocumentId(Long documentId);
    List<LmsLink> findByExternalSystemIdAndExternalDocId(String externalSystemId, String externalDocId);
}
