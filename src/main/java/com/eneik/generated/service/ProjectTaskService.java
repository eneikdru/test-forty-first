package com.eneik.generated.service;

import com.eneik.generated.model.ProjectTask;
import com.eneik.generated.repository.ProjectTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Service
public class ProjectTaskService {
    private static final Logger log = LoggerFactory.getLogger(ProjectTaskService.class);

    private final ProjectTaskRepository projectTaskRepository;
    private final GithubClient githubClient;
    private final TimeService timeService;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    public ProjectTaskService(ProjectTaskRepository projectTaskRepository, GithubClient githubClient, TimeService timeService, EntityManager entityManager, PlatformTransactionManager transactionManager) {
        this.projectTaskRepository = projectTaskRepository;
        this.githubClient = githubClient;
        this.timeService = timeService;
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public boolean transitionTaskState(String taskId, String oldStatus, String newStatus) {
        log.info("[ProjectTaskService] Transitioning task {} from {} to {}", taskId, oldStatus, newStatus);

        // Execute the database update within a transaction and return the task if a GitHub call is needed
        ProjectTask taskToClose = transactionTemplate.execute(status -> {
            int updatedCount = projectTaskRepository.updateSessionStatusAtomically(
                    taskId, oldStatus, newStatus, timeService.getCurrentTime());

            if (updatedCount > 0) {
                entityManager.flush();
                entityManager.clear();
                if ("closed_terminal_task".equals(newStatus)) {
                    return projectTaskRepository.findByTaskId(taskId).orElse(null);
                }
                // Signal success but no GitHub action needed
                return new ProjectTask();
            } else {
                log.warn("[ProjectTaskService] Failed to transition task {}. It might not exist or its status is not {}", taskId, oldStatus);
                return null;
            }
        });

        // Now, OUTSIDE the transaction, perform the synchronous HTTP network IO
        if (taskToClose != null) {
            if (taskToClose.getId() != null && taskToClose.getPrNumber() != null) {
                log.info("[ProjectTaskService] Task {} reached terminal state. Closing associated PR #{}", taskId, taskToClose.getPrNumber());
                githubClient.closePullRequest(taskToClose.getPrNumber());
            }
            return true; // We successfully updated the database
        }

        return false;
    }
}
