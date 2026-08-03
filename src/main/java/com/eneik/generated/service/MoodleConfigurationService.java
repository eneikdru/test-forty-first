package com.eneik.generated.service;

import com.eneik.generated.model.AuditLog;
import com.eneik.generated.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class MoodleConfigurationService {
    private static final Logger log = LoggerFactory.getLogger(MoodleConfigurationService.class);

    private final MoodleClient moodleClient;
    private final AuditLogRepository auditLogRepository;

    public MoodleConfigurationService(MoodleClient moodleClient, AuditLogRepository auditLogRepository) {
        this.moodleClient = moodleClient;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Executes the deterministic Moodle setup:
     * 1. Creates Budget, Workload, and Scholarship categories.
     * 2. Applies strict permission overrides per role (Administrator, Content-manager, Teacher, Student).
     */
    @Transactional
    public Map<String, Object> configureMoodle(String executorUserId, String executorUsername) {
        log.info("[MoodleConfigurationService] Starting Moodle deterministic configuration. Executor: {}", executorUsername);

        // 1. Prepare Category configurations
        List<Map<String, Object>> categoriesToCreate = new ArrayList<>();

        Map<String, Object> budgetCat = new LinkedHashMap<>();
        budgetCat.put("name", "Budget");
        budgetCat.put("idnumber", "edu_budget_finance");
        budgetCat.put("description", "Category for financial/budget and allocation planning");
        categoriesToCreate.add(budgetCat);

        Map<String, Object> workloadCat = new LinkedHashMap<>();
        workloadCat.put("name", "Workload");
        workloadCat.put("idnumber", "edu_staff_workload");
        workloadCat.put("description", "Category for human resources and staff workload");
        categoriesToCreate.add(workloadCat);

        Map<String, Object> scholarshipCat = new LinkedHashMap<>();
        scholarshipCat.put("name", "Scholarship");
        scholarshipCat.put("idnumber", "edu_scholarships");
        scholarshipCat.put("description", "Category for scholarships and educational stipends");
        categoriesToCreate.add(scholarshipCat);

        // Call client to create categories
        List<Map<String, Object>> createdCategories = moodleClient.createCategories(categoriesToCreate);

        // 2. Map and Apply strict permissions per role
        // Roles and their specific capabilities constraints (rigid & secure design)
        Map<String, Map<String, String>> rolePermissionsMap = new LinkedHashMap<>();

        // Administrator role permissions
        Map<String, String> adminCaps = new LinkedHashMap<>();
        adminCaps.put("moodle/category:manage", "allow");
        adminCaps.put("moodle/course:create", "allow");
        adminCaps.put("moodle/course:update", "allow");
        adminCaps.put("moodle/course:delete", "allow");
        adminCaps.put("moodle/course:view", "allow");
        rolePermissionsMap.put("Administrator", adminCaps);

        // Content-manager role permissions
        Map<String, String> contentManagerCaps = new LinkedHashMap<>();
        contentManagerCaps.put("moodle/category:manage", "allow");
        contentManagerCaps.put("moodle/course:create", "allow");
        contentManagerCaps.put("moodle/course:update", "allow");
        contentManagerCaps.put("moodle/course:delete", "prevent");
        contentManagerCaps.put("moodle/course:view", "allow");
        rolePermissionsMap.put("Content-manager", contentManagerCaps);

        // Teacher role permissions
        Map<String, String> teacherCaps = new LinkedHashMap<>();
        teacherCaps.put("moodle/category:manage", "prevent");
        teacherCaps.put("moodle/course:create", "prevent");
        teacherCaps.put("moodle/course:update", "allow");
        teacherCaps.put("moodle/course:delete", "prevent");
        teacherCaps.put("moodle/course:view", "allow");
        rolePermissionsMap.put("Teacher", teacherCaps);

        // Student role permissions (Strict restrictions on all operations except viewing)
        Map<String, String> studentCaps = new LinkedHashMap<>();
        studentCaps.put("moodle/category:manage", "prevent");
        studentCaps.put("moodle/course:create", "prevent");
        studentCaps.put("moodle/course:update", "prevent");
        studentCaps.put("moodle/course:delete", "prevent");
        studentCaps.put("moodle/course:view", "allow");
        rolePermissionsMap.put("Student", studentCaps);

        for (Map.Entry<String, Map<String, String>> entry : rolePermissionsMap.entrySet()) {
            moodleClient.applyRolePermissions(entry.getKey(), entry.getValue());
        }

        // 3. Record configuration run in the Audit log
        AuditLog auditLog = new AuditLog(
                executorUserId != null ? executorUserId : UUID.randomUUID().toString(),
                executorUsername != null ? executorUsername : "system",
                "MOODLE_CONFIG",
                "moodle_system",
                "edu_center_root",
                LocalDateTime.now()
        );
        auditLogRepository.save(auditLog);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "SUCCESS");
        result.put("createdCategories", createdCategories);
        result.put("configuredRolesCount", rolePermissionsMap.size());
        result.put("timestamp", LocalDateTime.now().toString());
        return result;
    }
}
