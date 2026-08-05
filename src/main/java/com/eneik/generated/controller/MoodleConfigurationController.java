package com.eneik.generated.controller;

import com.eneik.generated.service.MoodleConfigurationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.eneik.generated.security.JwtService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class MoodleConfigurationController {

    @Autowired
    private JwtService jwtService;


    private static final String DEFAULT_USER_ID = "ca078170-df17-48f8-bca4-d89000a6e87f";

    private static final String DEFAULT_USER_EMAIL = "anonymous@system.local";


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

        String username = DEFAULT_USER_EMAIL;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                username = jwtService.extractUsername(token);
            } catch (Exception e) {
                // Token invalid
                username = DEFAULT_USER_EMAIL;
            }
        }
        String userId = DEFAULT_USER_ID; // Administrator default UUID

        Map<String, Object> result = moodleConfigurationService.configureMoodle(userId, username);
        return ResponseEntity.ok(result);
    }
}
