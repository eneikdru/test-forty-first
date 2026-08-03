package com.eneik.generated.controller;

import com.eneik.generated.service.MoodleConfigurationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class MoodleConfigurationController {

    private final MoodleConfigurationService moodleConfigurationService;

    public MoodleConfigurationController(MoodleConfigurationService moodleConfigurationService) {
        this.moodleConfigurationService = moodleConfigurationService;
    }

    /**
     * POST endpoint to trigger the Moodle deterministic category and role configuration.
     * Accessible by system administrators or triggered via configuration scripts.
     */
    @PostMapping("/moodle/configure")
    public ResponseEntity<Map<String, Object>> configureMoodle(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String username = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : "ivan.ivanov@epidem.ru";
        String userId = "ca078170-df17-48f8-bca4-d89000a6e87f"; // Administrator default UUID

        Map<String, Object> result = moodleConfigurationService.configureMoodle(userId, username);
        return ResponseEntity.ok(result);
    }
}
