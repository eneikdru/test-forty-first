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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DefaultMoodleClient implements MoodleClient {
    private static final Logger log = LoggerFactory.getLogger(DefaultMoodleClient.class);

    @Value("${moodle.api.url:}")
    private String moodleApiUrl;

    @Value("${moodle.api.token:moodle_token_default_123}")
    private String moodleApiToken;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public List<Map<String, Object>> createCategories(List<Map<String, Object>> categories) {
        log.info("[MoodleClient] Attempting to create {} categories in Moodle", categories.size());

        if (moodleApiUrl != null && !moodleApiUrl.trim().isEmpty()) {
            String url = moodleApiUrl + "/webservice/rest/server.php";
            log.info("[MoodleClient] Post URL: {} with token: {}", url, moodleApiToken);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("wstoken", moodleApiToken);
            params.add("wsfunction", "core_course_create_categories");
            params.add("moodlewsrestformat", "json");

            for (int i = 0; i < categories.size(); i++) {
                Map<String, Object> cat = categories.get(i);
                params.add("categories[" + i + "][name]", (String) cat.get("name"));
                params.add("categories[" + i + "][idnumber]", (String) cat.get("idnumber"));
                params.add("categories[" + i + "][description]", (String) cat.get("description"));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> response = restTemplate.postForObject(url, request, List.class);
                log.info("[MoodleClient] Successfully received category creation response: {}", response);
                return response != null ? response : List.of();
            } catch (Exception e) {
                log.error("[MoodleClient] Failed to create Moodle categories", e);
                throw new RuntimeException("Failed to create course categories in Moodle", e);
            }
        }

        // Simulation/Fallback mode
        log.info("[MoodleClient] Running in simulation mode (no moodleApiUrl configured).");
        List<Map<String, Object>> simulatedResponse = new ArrayList<>();
        for (int i = 0; i < categories.size(); i++) {
            Map<String, Object> cat = categories.get(i);
            Map<String, Object> created = new HashMap<>();
            created.put("id", (long) (100 + i));
            created.put("name", cat.get("name"));
            created.put("idnumber", cat.get("idnumber"));
            simulatedResponse.add(created);
        }
        return simulatedResponse;
    }

    @Override
    public void applyRolePermissions(String roleName, Map<String, String> capabilities) {
        log.info("[MoodleClient] Applying {} strict capability permissions for role: {}", capabilities.size(), roleName);

        if (moodleApiUrl != null && !moodleApiUrl.trim().isEmpty()) {
            String url = moodleApiUrl + "/webservice/rest/server.php";

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("wstoken", moodleApiToken);
            params.add("wsfunction", "core_role_override_permissions");
            params.add("moodlewsrestformat", "json");
            params.add("role", roleName);

            int i = 0;
            for (Map.Entry<String, String> entry : capabilities.entrySet()) {
                params.add("permissions[" + i + "][capability]", entry.getKey());
                params.add("permissions[" + i + "][permission]", entry.getValue());
                i++;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            try {
                restTemplate.postForObject(url, request, String.class);
                log.info("[MoodleClient] Successfully applied permissions to Moodle for role: {}", roleName);
            } catch (Exception e) {
                log.error("[MoodleClient] Failed to apply capabilities override for role: " + roleName, e);
                throw new RuntimeException("Failed to apply capability overrides in Moodle", e);
            }
        } else {
            log.info("[MoodleClient] Simulated applying strict permissions for role: {}. Capabilities: {}", roleName, capabilities);
        }
    }
}
