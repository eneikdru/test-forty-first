package com.eneik.generated.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EiosIntegrationScheduler {
    private static final Logger log = LoggerFactory.getLogger(EiosIntegrationScheduler.class);

    private final EiosSyncService eiosSyncService;

    public EiosIntegrationScheduler(EiosSyncService eiosSyncService) {
        this.eiosSyncService = eiosSyncService;
    }

    /**
     * Given a scheduled job, When running, Then it syncs EIOS roles and exports analytics.
     * We run this periodically. Configurable via application.properties or default cron.
     */
    @Scheduled(cron = "${eios.sync.cron:0 0 1 * * ?}")
    public void runEiosSyncAndExport() {
        log.info("[EiosIntegrationScheduler] Triggered EIOS role sync and analytics export scheduled task.");
        eiosSyncService.syncRoles();
        eiosSyncService.exportAnalytics();
        log.info("[EiosIntegrationScheduler] Completed scheduled tasks.");
    }
}
