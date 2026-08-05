package com.eneik.generated.repository;

import com.eneik.generated.model.ActualizationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActualizationRequestRepository extends JpaRepository<ActualizationRequest, Long> {
    List<ActualizationRequest> findByDocumentId(Long documentId);
}
