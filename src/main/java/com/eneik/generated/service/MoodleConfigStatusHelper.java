package com.eneik.generated.service;

import com.eneik.generated.repository.MoodleConfigStatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MoodleConfigStatusHelper {

    private final MoodleConfigStatusRepository moodleConfigStatusRepository;

    public MoodleConfigStatusHelper(MoodleConfigStatusRepository moodleConfigStatusRepository) {
        this.moodleConfigStatusRepository = moodleConfigStatusRepository;
    }

    @Transactional
    public boolean tryStartConfiguring(LocalDateTime now) {
        int rowsUpdated = moodleConfigStatusRepository.updateStatusAtomically(
                "MOODLE_CONFIG",
                "CONFIGURING",
                List.of("PENDING", "COMPLETED", "FAILED"),
                now
        );
        return rowsUpdated > 0;
    }

    @Transactional
    public void markCompleted() {
        moodleConfigStatusRepository.updateStatusAtomicallySingle(
                "MOODLE_CONFIG",
                "COMPLETED",
                "CONFIGURING",
                LocalDateTime.now()
        );
    }

    @Transactional
    public void markFailed() {
        moodleConfigStatusRepository.updateStatusAtomicallySingle(
                "MOODLE_CONFIG",
                "FAILED",
                "CONFIGURING",
                LocalDateTime.now()
        );
    }
}
