package com.eneik.generated.service;

import java.util.List;
import java.util.Map;

public interface MoodleClient {
    /**
     * Creates course categories in Moodle using 'core_course_create_categories'.
     *
     * @param categories list of categories to create, containing name, idnumber, description.
     * @return the created category response details from Moodle.
     */
    List<Map<String, Object>> createCategories(List<Map<String, Object>> categories);

    /**
     * Configures/applies strict capability permissions per role in Moodle.
     * Uses standard/custom capability mapping to restrict or permit actions.
     *
     * @param roleName the system role name (e.g., "Administrator", "Content-manager", "Teacher", "Student")
     * @param capabilities map of capability name to permission level (e.g. "allow", "prevent", "prohibit")
     */
    void applyRolePermissions(String roleName, Map<String, String> capabilities);
}
