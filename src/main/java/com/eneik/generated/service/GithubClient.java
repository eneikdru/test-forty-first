package com.eneik.generated.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class GithubClient {
    private static final Logger log = LoggerFactory.getLogger(GithubClient.class);

    @Value("${github.api.url:https://api.github.com}")
    private String githubApiUrl;

    @Value("${github.owner:eneik}")
    private String owner;

    @Value("${github.repo:test-repo}")
    private String repo;

    @Value("${github.token:}")
    private String token;

    private final HttpClient httpClient;

    public GithubClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void closePullRequest(int prNumber) {
        log.info("[GithubClient] Attempting to close PR #{} in {}/{}", prNumber, owner, repo);

        String url = String.format("%s/repos/%s/%s/pulls/%d", githubApiUrl, owner, repo, prNumber);
        String body = "{\"state\":\"closed\"}";

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/vnd.github.v3+json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body));

        if (token != null && !token.trim().isEmpty()) {
            requestBuilder.header("Authorization", "token " + token);
        }

        HttpRequest request = requestBuilder.build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("[GithubClient] Successfully closed PR #{}", prNumber);
            } else {
                log.error("[GithubClient] Failed to close PR #{}. Status code: {}, Body: {}", prNumber, response.statusCode(), response.body());
                throw new RuntimeException("Failed to close PR in GitHub. Status: " + response.statusCode());
            }
        } catch (Exception e) {
            log.error("[GithubClient] Failed to close PR #" + prNumber, e);
            throw new RuntimeException("Failed to close PR in GitHub", e);
        }
    }
}
