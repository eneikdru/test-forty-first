package com.eneik.generated.service;

import com.eneik.generated.model.EiosExportRecord;
import com.eneik.generated.model.EiosRole;
import com.eneik.generated.model.Role;
import com.eneik.generated.repository.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class EiosSyncService {
    private static final Logger log = LoggerFactory.getLogger(EiosSyncService.class);

    private final EiosClient eiosClient;
    private final RoleRepository roleRepository;
    private final LmsIntegrationService lmsIntegrationService;
    private final TimeService timeService;

    public EiosSyncService(EiosClient eiosClient,
                           RoleRepository roleRepository,
                           LmsIntegrationService lmsIntegrationService,
                           TimeService timeService) {
        this.eiosClient = eiosClient;
        this.roleRepository = roleRepository;
        this.lmsIntegrationService = lmsIntegrationService;
        this.timeService = timeService;
    }

    /**
     * Given a scheduled job, When running, Then it syncs EIOS roles.
     */
    @Transactional
    public void syncRoles() {
        log.info("[EiosSyncService] Running EIOS role synchronization job...");
        try {
            List<EiosRole> eiosRoles = eiosClient.fetchRoles();
            if (eiosRoles == null || eiosRoles.isEmpty()) {
                log.warn("[EiosSyncService] No roles fetched from EIOS API.");
                return;
            }

            for (EiosRole eiosRole : eiosRoles) {
                Optional<Role> existingOpt = roleRepository.findByName(eiosRole.getName());
                if (existingOpt.isPresent()) {
                    Role existing = existingOpt.get();
                    existing.setDescription(eiosRole.getDescription());
                    roleRepository.save(existing);
                    log.info("[EiosSyncService] Updated existing role: {}", eiosRole.getName());
                } else {
                    Role newRole = new Role(eiosRole.getName(), eiosRole.getDescription());
                    roleRepository.save(newRole);
                    log.info("[EiosSyncService] Created new role from EIOS sync: {}", eiosRole.getName());
                }
            }
            log.info("[EiosSyncService] EIOS role synchronization completed successfully.");
        } catch (Exception e) {
            log.error("[EiosSyncService] EIOS role synchronization failed", e);
            throw new RuntimeException("EIOS role synchronization failed", e);
        }
    }

    /**
     * Given a scheduled job, When running, Then it exports analytics.
     */
    @Transactional(readOnly = true)
    public void exportAnalytics() {
        log.info("[EiosSyncService] Running EIOS analytics export job...");
        try {
            LocalDateTime now = timeService.getCurrentTime();
            LocalDateTime start = now.minusDays(1);
            LocalDateTime end = now;

            log.info("[EiosSyncService] Querying analytics between {} and {}", start, end);
            List<EiosExportRecord> records = lmsIntegrationService.exportToEiosFormat(start, end);

            eiosClient.sendAnalyticsExport(records);
            log.info("[EiosSyncService] EIOS analytics export completed successfully with {} records.", records.size());
        } catch (Exception e) {
            log.error("[EiosSyncService] EIOS analytics export failed", e);
            throw new RuntimeException("EIOS analytics export failed", e);
        }
    }
}
