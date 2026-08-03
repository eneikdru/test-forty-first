package com.eneik.generated.repository;

import com.eneik.generated.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    @Query("SELECT d FROM Document d WHERE " +
           "(:docType IS NULL OR LOWER(d.metadata) LIKE %:docType%) AND " +
           "(:specialty IS NULL OR LOWER(d.metadata) LIKE %:specialty%) AND " +
           "(:eduLevel IS NULL OR LOWER(d.metadata) LIKE %:eduLevel%) AND " +
           "(:categoryId IS NULL OR LOWER(d.metadata) LIKE %:categoryId%) AND " +
           "(:tag IS NULL OR LOWER(d.metadata) LIKE %:tag%)")
    List<Document> filterBase(
            @Param("docType") String docType,
            @Param("specialty") String specialty,
            @Param("eduLevel") String eduLevel,
            @Param("categoryId") String categoryId,
            @Param("tag") String tag
    );
}
