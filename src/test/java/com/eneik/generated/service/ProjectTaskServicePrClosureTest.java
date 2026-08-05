package com.eneik.generated.service;

import com.eneik.generated.model.ProjectTask;
import com.eneik.generated.repository.ProjectTaskRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProjectTaskServicePrClosureTest {

    @Mock
    private ProjectTaskRepository projectTaskRepository;

    @Mock
    private GithubClient githubClient;

    @Mock
    private TimeService timeService;

    @Mock
    private EntityManager entityManager;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ProjectTaskService projectTaskService;

    @BeforeEach
    public void setUp() {
        // Mock transaction status setup for TransactionTemplate execution
        TransactionStatus txStatus = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(txStatus);

        projectTaskService = new ProjectTaskService(
                projectTaskRepository,
                githubClient,
                timeService,
                entityManager,
                transactionManager
        );
    }

    @Test
    public void testTransitionToClosedTerminalTaskClosesPrExactlyOnce() {
        // Arrange: Given a mocked GitHub client and a task with an open PR
        String taskId = "test-task-uuid-12345";
        int prNumber = 42;
        String oldStatus = "in_progress";
        String newStatus = "closed_terminal_task";

        LocalDateTime fixedTime = LocalDateTime.of(2026, 8, 5, 12, 0, 0);
        when(timeService.getCurrentTime()).thenReturn(fixedTime);

        // Prepare the task with an open PR
        ProjectTask task = new ProjectTask(taskId, prNumber, oldStatus);
        // Set id to non-null to satisfy taskToClose.getId() != null condition
        task.setId(100L);

        // Mock repository behaviour
        when(projectTaskRepository.updateSessionStatusAtomically(
                eq(taskId), eq(oldStatus), eq(newStatus), eq(fixedTime)
        )).thenReturn(1);

        when(projectTaskRepository.findByTaskId(eq(taskId))).thenReturn(Optional.of(task));

        // Act: When the task is transitioned to closed_terminal_task
        boolean success = projectTaskService.transitionTaskState(taskId, oldStatus, newStatus);

        // Assert: Then the test must assert that the GitHub PR close method was called exactly once
        assertTrue(success, "Transition must be reported as successful");
        verify(githubClient, times(1)).closePullRequest(prNumber);
    }

    @Test
    public void testTransitionToClosedTerminalTaskFailsWhenPrClosureThrowsException() {
        // Arrange: Given a mocked GitHub client that throws exception and a task with an open PR
        String taskId = "test-task-uuid-rollback";
        int prNumber = 99;
        String oldStatus = "in_progress";
        String newStatus = "closed_terminal_task";

        LocalDateTime fixedTime = LocalDateTime.of(2026, 8, 5, 12, 0, 0);
        when(timeService.getCurrentTime()).thenReturn(fixedTime);

        ProjectTask task = new ProjectTask(taskId, prNumber, oldStatus);
        // Set id to non-null to satisfy taskToClose.getId() != null condition
        task.setId(101L);

        when(projectTaskRepository.updateSessionStatusAtomically(
                eq(taskId), eq(oldStatus), eq(newStatus), eq(fixedTime)
        )).thenReturn(1);

        when(projectTaskRepository.findByTaskId(eq(taskId))).thenReturn(Optional.of(task));

        // Stub GitHub client to throw exception
        doThrow(new RuntimeException("GitHub API down")).when(githubClient).closePullRequest(prNumber);

        // Act & Assert: Transitioning should throw the exception and trigger rollback
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> {
            projectTaskService.transitionTaskState(taskId, oldStatus, newStatus);
        });

        // Verify that PlatformTransactionManager rollback was called due to the exception
        verify(transactionManager, times(1)).rollback(any(TransactionStatus.class));
    }
}
