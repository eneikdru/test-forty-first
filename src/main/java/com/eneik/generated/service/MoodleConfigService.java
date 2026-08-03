package com.eneik.generated.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class MoodleConfigService {

    private static final Logger log = LoggerFactory.getLogger(MoodleConfigService.class);

    private final MoodleConfigStatusHelper statusHelper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${moodle.api.url:}")
    private String moodleApiUrl;

    @Value("${moodle.api.token:}")
    private String moodleApiToken;

    public MoodleConfigService(MoodleConfigStatusHelper statusHelper) {
        this.statusHelper = statusHelper;
    }

    /**
     * Triggers deterministic configuration of Moodle categories and roles/permissions.
     * This orchestrator does NOT run in a transaction, preventing transaction resource hold during remote API calls.
     */
    public void configureMoodle() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Try starting configuring atomically via helper (runs in transaction)
        boolean acquired = statusHelper.tryStartConfiguring(now);
        if (!acquired) {
            log.warn("[MoodleConfigService] Configuration is already running or database status check failed.");
            throw new IllegalStateException("Another Moodle configuration process is already in progress.");
        }

        try {
            log.info("[MoodleConfigService] Transitioned to CONFIGURING. Starting Moodle configuration...");

            if (moodleApiUrl == null || moodleApiUrl.trim().isEmpty() || moodleApiToken == null || moodleApiToken.trim().isEmpty()) {
                log.info("[MoodleConfigService] Moodle API URL or Token is not configured. Running mock-only configuration mode.");
                statusHelper.markCompleted();
                return;
            }

            // Fetch existing categories from Moodle to support Idempotency
            Map<String, Integer> existingCategories = fetchExistingCategories();

            // 1. Create top-level category: edu_center_root (if not already existing)
            int rootCategoryId;
            if (existingCategories.containsKey("edu_center_root")) {
                rootCategoryId = existingCategories.get("edu_center_root");
                log.info("[MoodleConfigService] Root category 'edu_center_root' already exists with ID: {}", rootCategoryId);
            } else {
                rootCategoryId = createCategory("Education Center Root", "edu_center_root", 0, "Top-level category for the educational center.");
            }

            // 2. Create subcategories under edu_center_root (if not already existing)
            int budgetId = getOrCreateSubcategory("Budget", "edu_budget_finance", rootCategoryId, "Financial documents, budgets, and financial reporting.", existingCategories);
            int workloadId = getOrCreateSubcategory("Workload", "edu_staff_workload", rootCategoryId, "Faculty teaching hours, staff distributions, and workloads.", existingCategories);
            int scholarshipsId = getOrCreateSubcategory("Scholarships", "edu_scholarships", rootCategoryId, "Student stipend structures, criteria, and scholarship orders.", existingCategories);
            int reportsId = getOrCreateSubcategory("Reports", "edu_academic_reports", rootCategoryId, "General institutional reporting, exam reports, and audits.", existingCategories);

            // 3. Apply strict role assignments (strict permissions) per category context
            // According to moodle_config_plan.md Section 4.1:
            // Admin (Role ID 1) has access to all.
            // Content Manager (Role ID 2) has access to Workload, Scholarships, Reports.
            // Teacher (Role ID 3) has access to Workload, Scholarships, Reports.
            // Student (Role ID 4) has access to Scholarships.

            // Assign Administrator (User ID 10, Role ID 1) to all subcategories
            assignRole(1, 10, budgetId);
            assignRole(1, 10, workloadId);
            assignRole(1, 10, scholarshipsId);
            assignRole(1, 10, reportsId);

            // Assign Content Manager (User ID 20, Role ID 2) to Workload, Scholarships, Reports
            assignRole(2, 20, workloadId);
            assignRole(2, 20, scholarshipsId);
            assignRole(2, 20, reportsId);

            // Assign Teacher (User ID 30, Role ID 3) to Workload, Scholarships, Reports
            assignRole(3, 30, workloadId);
            assignRole(3, 30, scholarshipsId);
            assignRole(3, 30, reportsId);

            // Assign Student (User ID 40, Role ID 4) to Scholarships
            assignRole(4, 40, scholarshipsId);

            log.info("[MoodleConfigService] All categories created/verified and roles assigned successfully.");

            // Transition status to COMPLETED (runs in transaction)
            statusHelper.markCompleted();

        } catch (Exception e) {
            log.error("[MoodleConfigService] Configuration failed", e);
            // Transition status to FAILED (runs in transaction)
            statusHelper.markFailed();
            throw new RuntimeException("Moodle configuration failed: " + e.getMessage(), e);
        }
    }

    private Map<String, Integer> fetchExistingCategories() {
        String url = moodleApiUrl + "?wstoken=" + moodleApiToken + "&wsfunction=core_course_get_categories&moodlewsrestformat=json";
        try {
            log.info("[MoodleConfigService] Fetching existing Moodle categories for idempotency check...");
            List<?> response = restTemplate.getForObject(url, List.class);
            Map<String, Integer> categoryMap = new HashMap<>();
            if (response != null) {
                for (Object obj : response) {
                    if (obj instanceof Map) {
                        Map<?, ?> map = (Map<?, ?>) obj;
                        String idnumber = (String) map.get("idnumber");
                        Object idVal = map.get("id");
                        if (idnumber != null && !idnumber.trim().isEmpty() && idVal instanceof Number) {
                            categoryMap.put(idnumber, ((Number) idVal).intValue());
                        }
                    }
                }
            }
            return categoryMap;
        } catch (Exception e) {
            log.warn("[MoodleConfigService] Failed to fetch existing categories from Moodle. Proceeding under assumption of empty instance.", e);
            return Collections.emptyMap();
        }
    }

    private int getOrCreateSubcategory(String name, String idnumber, int parentId, String description, Map<String, Integer> existingCategories) {
        if (existingCategories.containsKey(idnumber)) {
            int existingId = existingCategories.get(idnumber);
            log.info("[MoodleConfigService] Subcategory '{}' already exists with ID: {}", idnumber, existingId);
            return existingId;
        }
        return createCategory(name, idnumber, parentId, description);
    }

    private int createCategory(String name, String idnumber, int parentId, String description) {
        String url = moodleApiUrl + "?wstoken=" + moodleApiToken + "&wsfunction=core_course_create_categories&moodlewsrestformat=json";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("categories[0][name]", name);
        map.add("categories[0][idnumber]", idnumber);
        map.add("categories[0][parent]", String.valueOf(parentId));
        map.add("categories[0][description]", description);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        log.info("[MoodleConfigService] Creating category: {} ({}) under parent ID: {}", name, idnumber, parentId);
        List<?> responseList = restTemplate.postForObject(url, request, List.class);

        if (responseList == null || responseList.isEmpty()) {
            throw new RuntimeException("Empty or invalid response from Moodle when creating category: " + idnumber);
        }

        Map<?, ?> categoryMap = (Map<?, ?>) responseList.get(0);
        Object idVal = categoryMap.get("id");
        if (idVal instanceof Number) {
            return ((Number) idVal).intValue();
        } else if (idVal instanceof String) {
            return Integer.parseInt((String) idVal);
        } else {
            throw new RuntimeException("Invalid category ID type returned from Moodle for category: " + idnumber);
        }
    }

    private void assignRole(int roleId, int userId, int contextId) {
        String url = moodleApiUrl + "?wstoken=" + moodleApiToken + "&wsfunction=core_role_assign&moodlewsrestformat=json";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("assignments[0][roleid]", String.valueOf(roleId));
        map.add("assignments[0][userid]", String.valueOf(userId));
        map.add("assignments[0][contextid]", String.valueOf(contextId));

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        log.info("[MoodleConfigService] Assigning role ID: {} to user ID: {} in context ID: {}", roleId, userId, contextId);
        restTemplate.postForObject(url, request, Object.class);
    }

    public String getMoodleApiUrl() {
        return moodleApiUrl;
    }

    public void setMoodleApiUrl(String moodleApiUrl) {
        this.moodleApiUrl = moodleApiUrl;
    }

    public String getMoodleApiToken() {
        return moodleApiToken;
    }

    public void setMoodleApiToken(String moodleApiToken) {
        this.moodleApiToken = moodleApiToken;
    }
}
