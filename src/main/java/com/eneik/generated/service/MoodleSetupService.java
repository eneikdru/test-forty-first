package com.eneik.generated.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class MoodleSetupService {

    private final RestTemplate restTemplate;
    private final String moodleBaseUrl;
    private final String token;

    public MoodleSetupService(RestTemplate restTemplate,
                              @Value("${moodle.api.url:http://localhost/webservice/rest/server.php}") String moodleBaseUrl,
                              @Value("${moodle.api.token:dummy-token}") String token) {
        this.restTemplate = restTemplate;
        this.moodleBaseUrl = moodleBaseUrl;
        this.token = token;
    }

    public void executeSetup() {
        // Create required categories
        createCategory("Budget");
        createCategory("Workload");
        createCategory("Scholarship");

        // Apply strict permissions per role as per acceptance criteria
        applyRolePermissions("Администратор", "strict");
        applyRolePermissions("Контент-менеджер", "strict");
        applyRolePermissions("Преподаватель / научный руководитель", "strict");
        applyRolePermissions("Ординатор / аспирант / слушатель", "strict");
    }

    private void createCategory(String categoryName) {
        String url = String.format("%s?wstoken=%s&wsfunction=core_course_create_categories&moodlewsrestformat=json", moodleBaseUrl, token);
        Map<String, Object> request = Map.of(
            "categories", List.of(Map.of("name", categoryName))
        );
        restTemplate.postForEntity(url, request, String.class);
    }

    private void applyRolePermissions(String roleName, String permissionLevel) {
        String url = String.format("%s?wstoken=%s&wsfunction=local_api_apply_permissions&moodlewsrestformat=json", moodleBaseUrl, token);
        Map<String, Object> request = Map.of(
            "role", roleName,
            "permissionLevel", permissionLevel
        );
        restTemplate.postForEntity(url, request, String.class);
    }
}
