package com.github.searcher.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.github.searcher.dto.SearchRequest;
import com.github.searcher.handler.GitHubApiException;
import com.github.searcher.handler.GitHubRateLimitExceededException;
import com.github.searcher.model.GitHubRepository;
import com.github.searcher.service.GitHubService;

import reactor.core.publisher.Mono;

@WebFluxTest(GitHubController.class)
class GitHubControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private GitHubService gitHubService;

    private GitHubRepository createTestRepository(Long id, String name, String description, String owner, String language, Integer stars, Integer forks, String lastUpdated) {
        return new GitHubRepository(id, name, description, owner, language, stars, forks, OffsetDateTime.parse(lastUpdated, DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

    @Test
    @DisplayName("POST /api/github/search - Should return 200 OK and fetched repositories on success")
    void searchGitHubRepositories_success() throws Exception {
        SearchRequest searchRequest = new SearchRequest("spring boot", "Java", "stars");
        GitHubRepository repo = createTestRepository(123L, "spring-boot-starter", "Starter for Spring Boot", "spring-projects", "Java", 1000, 200, "2024-01-01T12:00:00Z");

        when(gitHubService.searchAndSaveRepositories(any(SearchRequest.class)))
                .thenReturn((Mono<List<GitHubRepository>>) Mono.just(Collections.singletonList(repo)));

        webTestClient.post().uri("/api/github/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(searchRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Repositories fetched and saved successfully")
                .jsonPath("$.repositories[0].name").isEqualTo("spring-boot-starter")
                .jsonPath("$.repositories[0].starsCount").isEqualTo(1000);
    }

    @Test
    @DisplayName("POST /api/github/search - Should return 400 Bad Request on validation error (empty query)")
    void searchGitHubRepositories_validationError_emptyQuery() throws Exception {
        SearchRequest searchRequest = new SearchRequest("", "Java", "stars");

        webTestClient.post().uri("/api/github/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(searchRequest)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.query").isEqualTo("Query cannot be empty");
    }

    @Test
    @DisplayName("POST /api/github/search - Should return 429 Too Many Requests on GitHub API rate limit")
    void searchGitHubRepositories_rateLimitExceeded() throws Exception {
        SearchRequest searchRequest = new SearchRequest("test", null, null);

        when(gitHubService.searchAndSaveRepositories(any(SearchRequest.class)))
                .thenReturn(Mono.error(new GitHubRateLimitExceededException("GitHub API rate limit exceeded.", 60)));

        webTestClient.post().uri("/api/github/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(searchRequest)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectBody()
                .jsonPath("$.error").isEqualTo("GitHub API Rate Limit Exceeded")
                .jsonPath("$.message").isEqualTo("GitHub API rate limit exceeded.")
                .jsonPath("$.retryAfterSeconds").isEqualTo(60);
    }

    @Test
    @DisplayName("POST /api/github/search - Should return 400 Bad Request on generic GitHub API client error")
    void searchGitHubRepositories_githubApiClientError() throws Exception {
        SearchRequest searchRequest = new SearchRequest("invalid-query", null, null);

        when(gitHubService.searchAndSaveRepositories(any(SearchRequest.class)))
                .thenReturn(Mono.error(new GitHubApiException("GitHub API client error: Missing 'q' parameter.", HttpStatus.BAD_REQUEST)));

        webTestClient.post().uri("/api/github/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(searchRequest)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo("GitHub API Error")
                .jsonPath("$.message").isEqualTo("GitHub API client error: Missing 'q' parameter.");
    }

    @Test
    @DisplayName("GET /api/github/repositories - Should return 200 OK and all stored repositories")
    void getStoredRepositories_success() throws Exception {
        GitHubRepository repo1 = createTestRepository(1L, "repo-java", "Desc Java", "ownerA", "Java", 500, 50, "2024-01-01T12:00:00Z");
        GitHubRepository repo2 = createTestRepository(2L, "repo-python", "Desc Python", "ownerB", "Python", 300, 30, "2024-01-02T12:00:00Z");

        when(gitHubService.getStoredRepositories(any(), any(), any()))
                .thenReturn(Mono.just(Arrays.asList(repo1, repo2)));

        webTestClient.get().uri(uriBuilder -> uriBuilder.path("/api/github/repositories")
                        .queryParam("language", "Java")
                        .queryParam("minStars", "100")
                        .queryParam("sort", "stars")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.repositories").isArray()
                .jsonPath("$.repositories").value(hasSize(2))
                .jsonPath("$.repositories[0].name").isEqualTo("repo-java")
                .jsonPath("$.repositories[1].language").isEqualTo("Python");
    }

    @Test
    @DisplayName("GET /api/github/repositories - Should return 200 OK and empty list if no results")
    void getStoredRepositories_noResults() throws Exception {
        when(gitHubService.getStoredRepositories(any(), any(), any()))
                .thenReturn(Mono.just(Collections.emptyList()));

        webTestClient.get().uri(uriBuilder -> uriBuilder.path("/api/github/repositories")
                        .queryParam("language", "NonExistentLanguage")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.repositories").isArray()
                .jsonPath("$.repositories").value(hasSize(0));
    }
}
