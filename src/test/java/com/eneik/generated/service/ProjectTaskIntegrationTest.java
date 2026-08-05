package com.eneik.generated.service;

import com.eneik.generated.model.ProjectTask;
import com.eneik.generated.repository.ProjectTaskRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class ProjectTaskIntegrationTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private ProjectTaskService projectTaskService;

    @Autowired
    private ProjectTaskRepository projectTaskRepository;

    @Autowired
    private TimeService timeService;

    @Autowired
    private EntityManager entityManager;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        registry.add("github.api.url", () -> "http://localhost:" + wireMockServer.port());
        registry.add("github.owner", () -> "eneik");
        registry.add("github.repo", () -> "test-repo");
        registry.add("github.token", () -> "fake_token");
    }

    @AfterAll
    static void stopServer() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    public void setup() {
        WireMock.configureFor("localhost", wireMockServer.port());
        wireMockServer.resetAll();
    }

    @Test
    public void testTaskTransitionToTerminalStateClosesPR() {
        // Stub the GitHub PR PATCH endpoint
        stubFor(patch(urlEqualTo("/repos/eneik/test-repo/pulls/74"))
                .withHeader("Authorization", equalTo("token fake_token"))
                .withRequestBody(matchingJsonPath("$.state", equalTo("closed")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"url\": \"https://api.github.com/repos/eneik/test-repo/pulls/74\"}")));

        // Create a new task with an active PR
        ProjectTask task = new ProjectTask("ce3bd3b1-28aa-4b1c-86ee-183ad6f23fb8", 74, "in_progress");
        task.setLastUpdated(timeService.getCurrentTime());
        projectTaskRepository.save(task);

        // Transition the task to closed_terminal_task
        boolean success = projectTaskService.transitionTaskState("ce3bd3b1-28aa-4b1c-86ee-183ad6f23fb8", "in_progress", "closed_terminal_task");

        assertTrue(success, "Task transition should be successful");

        entityManager.clear();

        // Verify the database state was updated atomically
        ProjectTask updatedTask = projectTaskRepository.findByTaskId("ce3bd3b1-28aa-4b1c-86ee-183ad6f23fb8").orElseThrow();
        assertEquals("closed_terminal_task", updatedTask.getSessionStatus(), "Session status should be updated");

        // Verify that the GitHub endpoint was called with the correct payload to close the PR
        verify(1, patchRequestedFor(urlEqualTo("/repos/eneik/test-repo/pulls/74"))
                .withRequestBody(matchingJsonPath("$.state", equalTo("closed")))
        );
    }

    @Test
    public void testTaskTransitionToNonTerminalStateDoesNotClosePR() {
        // Create a new task with an active PR
        ProjectTask task = new ProjectTask("task-123", 75, "in_progress");
        task.setLastUpdated(timeService.getCurrentTime());
        projectTaskRepository.save(task);

        // Transition the task to another non-terminal state
        boolean success = projectTaskService.transitionTaskState("task-123", "in_progress", "in_review");

        assertTrue(success, "Task transition should be successful");

        entityManager.clear();

        // Verify the database state was updated
        ProjectTask updatedTask = projectTaskRepository.findByTaskId("task-123").orElseThrow();
        assertEquals("in_review", updatedTask.getSessionStatus());

        // Verify that the GitHub endpoint was NOT called
        verify(0, patchRequestedFor(anyUrl()));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void testTaskTransitionToTerminalStateRollsBackOnPRClosureFailure() {
        // Stub the GitHub PR PATCH endpoint to return a 500 server error
        stubFor(patch(urlEqualTo("/repos/eneik/test-repo/pulls/88"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        // Create a new task with an active PR
        ProjectTask task = new ProjectTask("task-failing-pr", 88, "in_progress");
        task.setLastUpdated(timeService.getCurrentTime());
        projectTaskRepository.saveAndFlush(task);

        // Transition should throw RuntimeException due to the GitHub API failure
        assertThrows(RuntimeException.class, () -> {
            projectTaskService.transitionTaskState("task-failing-pr", "in_progress", "closed_terminal_task");
        });

        // Verify that because of the exception, the database transaction was rolled back.
        // Therefore, the task's session status remains unchanged ("in_progress").
        ProjectTask rolledBackTask = projectTaskRepository.findByTaskId("task-failing-pr").orElseThrow();
        assertEquals("in_progress", rolledBackTask.getSessionStatus(), "Session status should be rolled back and remain in_progress");

        // Clean up
        projectTaskRepository.delete(rolledBackTask);
        projectTaskRepository.flush();
    }

    @Test
    public void testTaskTransitionToTerminalStateNoPR() {
        // Create a new task with NO active PR
        ProjectTask task = new ProjectTask("task-no-pr", null, "in_progress");
        task.setLastUpdated(timeService.getCurrentTime());
        projectTaskRepository.save(task);

        // Transition the task to closed_terminal_task
        boolean success = projectTaskService.transitionTaskState("task-no-pr", "in_progress", "closed_terminal_task");

        assertTrue(success, "Task transition should be successful even without PR");

        // Clear persistence context to read fresh state from database
        entityManager.clear();

        // Verify the database state was successfully updated to closed_terminal_task
        ProjectTask updatedTask = projectTaskRepository.findByTaskId("task-no-pr").orElseThrow();
        assertEquals("closed_terminal_task", updatedTask.getSessionStatus());

        // Verify that the GitHub endpoint was NOT called
        verify(0, patchRequestedFor(anyUrl()));
    }

    @Test
    public void testAtomicUpdatePreventsLostUpdates() {
        // Create a new task
        ProjectTask task = new ProjectTask("task-atomic", 76, "in_progress");
        task.setLastUpdated(timeService.getCurrentTime());
        projectTaskRepository.save(task);

        // Attempt to transition with an incorrect old status (simulating a concurrent update that changed the status)
        boolean success = projectTaskService.transitionTaskState("task-atomic", "in_review", "closed_terminal_task");

        assertFalse(success, "Transition should fail due to atomic check on old status");

        // Verify the database state remains unchanged
        ProjectTask unchangedTask = projectTaskRepository.findByTaskId("task-atomic").orElseThrow();
        assertEquals("in_progress", unchangedTask.getSessionStatus());

        // Verify that the GitHub endpoint was NOT called
        verify(0, patchRequestedFor(anyUrl()));
    }
}
