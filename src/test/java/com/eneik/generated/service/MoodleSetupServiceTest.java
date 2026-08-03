package com.eneik.generated.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {MoodleSetupService.class})
public class MoodleSetupServiceTest {

    @MockBean
    private RestTemplate restTemplate;

    @Autowired
    private MoodleSetupService moodleSetupService;

    @BeforeEach
    public void setup() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("success"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testExecuteSetup() {
        moodleSetupService.executeSetup();

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> requestCaptor = ArgumentCaptor.forClass(Map.class);

        // Verify categories are created
        verify(restTemplate, times(7)).postForEntity(urlCaptor.capture(), requestCaptor.capture(), eq(String.class));

        List<String> urls = urlCaptor.getAllValues();
        List<Map<String, Object>> requests = requestCaptor.getAllValues();

        int categoryCreations = 0;
        boolean budgetCreated = false;
        boolean workloadCreated = false;
        boolean scholarshipCreated = false;

        int permissionApplications = 0;
        boolean adminPermissionsApplied = false;
        boolean contentManagerPermissionsApplied = false;
        boolean teacherPermissionsApplied = false;
        boolean studentPermissionsApplied = false;

        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            Map<String, Object> request = requests.get(i);

            if (url.contains("core_course_create_categories")) {
                categoryCreations++;
                List<Map<String, String>> categories = (List<Map<String, String>>) request.get("categories");
                String name = categories.get(0).get("name");
                if ("Budget".equals(name)) budgetCreated = true;
                if ("Workload".equals(name)) workloadCreated = true;
                if ("Scholarship".equals(name)) scholarshipCreated = true;
            } else if (url.contains("local_api_apply_permissions")) {
                permissionApplications++;
                String role = (String) request.get("role");
                if ("Администратор".equals(role)) adminPermissionsApplied = true;
                if ("Контент-менеджер".equals(role)) contentManagerPermissionsApplied = true;
                if ("Преподаватель / научный руководитель".equals(role)) teacherPermissionsApplied = true;
                if ("Ординатор / аспирант / слушатель".equals(role)) studentPermissionsApplied = true;
                assertEquals("strict", request.get("permissionLevel"));
            }
        }

        assertEquals(3, categoryCreations, "Exactly 3 categories should be created");
        assertTrue(budgetCreated, "Budget category should be created");
        assertTrue(workloadCreated, "Workload category should be created");
        assertTrue(scholarshipCreated, "Scholarship category should be created");

        assertEquals(4, permissionApplications, "Exactly 4 roles should have permissions applied");
        assertTrue(adminPermissionsApplied, "Admin permissions should be applied");
        assertTrue(contentManagerPermissionsApplied, "Content manager permissions should be applied");
        assertTrue(teacherPermissionsApplied, "Teacher permissions should be applied");
        assertTrue(studentPermissionsApplied, "Student permissions should be applied");
    }
}
