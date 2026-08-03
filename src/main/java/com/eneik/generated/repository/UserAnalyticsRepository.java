package com.eneik.generated.repository;

import com.eneik.generated.model.UserAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserAnalyticsRepository extends JpaRepository<UserAnalytics, Long> {
    List<UserAnalytics> findByUserId(String userId);
    List<UserAnalytics> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
}
