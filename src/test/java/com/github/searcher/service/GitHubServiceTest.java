package com.github.searcher.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.searcher.dto.SearchRequest;
import com.github.searcher.handler.GitHubApiException;
import com.github.searcher.handler.GitHubRateLimitExceededException;
import com.github.searcher.model.GitHubRepository;
import com.github.searcher.repository.GitHubRepositoryRepository;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class GitHubServiceTest {

    @Mock
    private WebClient webClient;
    @Mock
    private GitHubRepositoryRepository gitHubRepositoryRepository;

    @Mock private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock private WebClient.ResponseSpec responseSpec;

    @InjectMocks
    private GitHubService gitHubService;

    private ObjectMapper objectMapper = new ObjectMapper();

    private GitHubRepository createTestRepository(Long id, String name, String description, String owner, String language, Integer stars, Integer forks, String lastUpdated) {
        return new GitHubRepository(id, name, description, owner, language, stars, forks, OffsetDateTime.parse(lastUpdated, DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(gitHubService, "searchRepositoriesPath", "/search/repositories");

        lenient().when(webClient.get()).thenReturn(requestHeadersUriSpec);
        lenient().when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        lenient().when(requestHeadersSpec.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)).thenReturn(requestHeadersSpec);
        lenient().when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        lenient().when(responseSpec.onStatus(any(Predicate.class), any(Function.class)))
                 .thenReturn(responseSpec);
    }

    @Test
    @DisplayName("Should successfully fetch and save new repositories")
    void searchAndSaveRepositories_successfulFetchAndSaveNew() throws Exception {
        SearchRequest searchRequest = new SearchRequest("test-repo", "Java", "stars");
        String githubApiResponse = "{\"items\":[{\"id\":1,\"name\":\"repo1\",\"description\":\"desc1\",\"owner\":{\"login\":\"owner1\"},\"language\":\"Java\",\"stargazers_count\":100,\"forks_count\":10,\"updated_at\":\"2023-01-01T12:00:00Z\"}]}";
        JsonNode jsonNode = objectMapper.readTree(githubApiResponse);

        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(jsonNode));
        when(gitHubRepositoryRepository.findById(anyLong())).thenReturn(Optional.empty());
        when(gitHubRepositoryRepository.save(any(GitHubRepository.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<GitHubRepository> result = gitHubService.searchAndSaveRepositories(searchRequest).block();

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals("repo1", result.get(0).getName());
        verify(gitHubRepositoryRepository, times(1)).save(any(GitHubRepository.class));
    }

    @Test
    @DisplayName("Should successfully fetch and update existing repositories")
    void searchAndSaveRepositories_successfulFetchAndUpdateExisting() throws Exception {
        SearchRequest searchRequest = new SearchRequest("test-repo", "Java", "stars");
        String githubApiResponse = "{\"items\":[{\"id\":1,\"name\":\"repo1_updated\",\"description\":\"desc1_updated\",\"owner\":{\"login\":\"owner1\"},\"language\":\"Java\",\"stargazers_count\":200,\"forks_count\":20,\"updated_at\":\"2024-01-01T12:00:00Z\"}]}";
        JsonNode jsonNode = objectMapper.readTree(githubApiResponse);

        GitHubRepository existingRepo = createTestRepository(1L, "repo1", "desc1", "owner1", "Java", 100, 10, "2023-01-01T12:00:00Z");

        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(jsonNode));
        when(gitHubRepositoryRepository.findById(1L)).thenReturn(Optional.of(existingRepo));
        when(gitHubRepositoryRepository.save(any(GitHubRepository.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<GitHubRepository> result = gitHubService.searchAndSaveRepositories(searchRequest).block();

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals("repo1_updated", result.get(0).getName());
        assertEquals(200, result.get(0).getStarsCount());
        verify(gitHubRepositoryRepository, times(1)).save(any(GitHubRepository.class));
    }

    @Test
    @DisplayName("Should not update repository if data is identical")
    void searchAndSaveRepositories_noUpdateIfIdentical() throws Exception {
        SearchRequest searchRequest = new SearchRequest("test-repo", "Java", "stars");
        String githubApiResponse = "{\"items\":[{\"id\":1,\"name\":\"repo1\",\"description\":\"desc1\",\"owner\":{\"login\":\"owner1\"},\"language\":\"Java\",\"stargazers_count\":100,\"forks_count\":10,\"updated_at\":\"2023-01-01T12:00:00Z\"}]}";
        JsonNode jsonNode = objectMapper.readTree(githubApiResponse);

        GitHubRepository existingRepo = createTestRepository(1L, "repo1", "desc1", "owner1", "Java", 100, 10, "2023-01-01T12:00:00Z");

        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(jsonNode));
        when(gitHubRepositoryRepository.findById(1L)).thenReturn(Optional.of(existingRepo));

        List<GitHubRepository> result = gitHubService.searchAndSaveRepositories(searchRequest).block();

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals("repo1", result.get(0).getName());
        verify(gitHubRepositoryRepository, never()).save(any(GitHubRepository.class));
    }

    @Test
    @DisplayName("Should return empty list if GitHub API returns no items")
    void searchAndSaveRepositories_noItemsFromGitHub() throws Exception {
        SearchRequest searchRequest = new SearchRequest("nonexistent", null, null);
        String githubApiResponse = "{\"items\":[]}";
        JsonNode jsonNode = objectMapper.readTree(githubApiResponse);

        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(jsonNode));

        List<GitHubRepository> result = gitHubService.searchAndSaveRepositories(searchRequest).block();

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(gitHubRepositoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw GitHubRateLimitExceededException on 403 Forbidden from GitHub API")
    void searchAndSaveRepositories_rateLimitExceeded() {
        SearchRequest searchRequest = new SearchRequest("test", null, null);

        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.error(
            new GitHubRateLimitExceededException("GitHub API rate limit exceeded. Please try again later.", 60)
        ));

        assertThrows(GitHubRateLimitExceededException.class,
                () -> gitHubService.searchAndSaveRepositories(searchRequest).block());

        verify(gitHubRepositoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw GitHubApiException on 4xx Client Error from GitHub API")
    void searchAndSaveRepositories_clientError() {
        SearchRequest searchRequest = new SearchRequest("invalid", null, null);

        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.error(
            new GitHubApiException("GitHub API client error: Invalid query parameter.", HttpStatus.BAD_REQUEST)
        ));

        GitHubApiException thrown = assertThrows(GitHubApiException.class,
                () -> gitHubService.searchAndSaveRepositories(searchRequest).block());

        assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
        assertTrue(thrown.getMessage().contains("Invalid query parameter"));
        verify(gitHubRepositoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw GitHubApiException on 5xx Server Error from GitHub API")
    void searchAndSaveRepositories_serverError() {
        SearchRequest searchRequest = new SearchRequest("test", null, null);

        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.error(
            new GitHubApiException("GitHub API server error: Internal server issue.", HttpStatus.INTERNAL_SERVER_ERROR)
        ));

        GitHubApiException thrown = assertThrows(GitHubApiException.class,
                () -> gitHubService.searchAndSaveRepositories(searchRequest).block());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, thrown.getStatusCode());
        assertTrue(thrown.getMessage().contains("Internal server issue"));
        verify(gitHubRepositoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should retrieve all stored repositories with default sorting")
    void getStoredRepositories_noFiltersDefaultSort() {
        GitHubRepository repo1 = createTestRepository(1L, "repoA", "desc", "ownerA", "Java", 200, 20, "2024-01-01T12:00:00Z");
        GitHubRepository repo2 = createTestRepository(2L, "repoB", "desc", "ownerB", "Python", 100, 10, "2024-01-02T12:00:00Z");
        List<GitHubRepository> mockRepos = Arrays.asList(repo1, repo2);

        when(gitHubRepositoryRepository.findAll(any(Specification.class), eq(Sort.by(Sort.Direction.DESC, "starsCount"))))
                .thenReturn(mockRepos);

        List<GitHubRepository> result = gitHubService.getStoredRepositories(null, null, null).block();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(repo1.getName(), result.get(0).getName());
        verify(gitHubRepositoryRepository, times(1)).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    @DisplayName("Should retrieve stored repositories filtered by language")
    void getStoredRepositories_filterByLanguage() {
        GitHubRepository repo1 = createTestRepository(1L, "repoA", "desc", "ownerA", "Java", 200, 20, "2024-01-01T12:00:00Z");
        List<GitHubRepository> mockRepos = Collections.singletonList(repo1);

        when(gitHubRepositoryRepository.findAll(any(Specification.class), eq(Sort.by(Sort.Direction.DESC, "starsCount"))))
                .thenReturn(mockRepos);

        List<GitHubRepository> result = gitHubService.getStoredRepositories("Java", null, null).block();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Java", result.get(0).getLanguage());
        verify(gitHubRepositoryRepository, times(1)).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    @DisplayName("Should retrieve stored repositories filtered by minimum stars")
    void getStoredRepositories_filterByMinStars() {
        GitHubRepository repo1 = createTestRepository(1L, "repoA", "desc", "ownerA", "Java", 500, 20, "2024-01-01T12:00:00Z");
        List<GitHubRepository> mockRepos = Collections.singletonList(repo1);

        when(gitHubRepositoryRepository.findAll(any(Specification.class), eq(Sort.by(Sort.Direction.DESC, "starsCount"))))
                .thenReturn(mockRepos);

        List<GitHubRepository> result = gitHubService.getStoredRepositories(null, 400, null).block();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).getStarsCount() >= 400);
        verify(gitHubRepositoryRepository, times(1)).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    @DisplayName("Should retrieve stored repositories sorted by forks")
    void getStoredRepositories_sortByForks() {
        GitHubRepository repo1 = createTestRepository(1L, "repoA", "desc", "ownerA", "Java", 200, 50, "2024-01-01T12:00:00Z");
        GitHubRepository repo2 = createTestRepository(2L, "repoB", "desc", "ownerB", "Python", 100, 100, "2024-01-02T12:00:00Z");
        List<GitHubRepository> mockRepos = Arrays.asList(repo2, repo1);

        when(gitHubRepositoryRepository.findAll(any(Specification.class), eq(Sort.by(Sort.Direction.DESC, "forksCount"))))
                .thenReturn(mockRepos);

        List<GitHubRepository> result = gitHubService.getStoredRepositories(null, null, "forks").block();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(repo2.getName(), result.get(0).getName());
        assertEquals(repo1.getName(), result.get(1).getName());
        verify(gitHubRepositoryRepository, times(1)).findAll(any(Specification.class), eq(Sort.by(Sort.Direction.DESC, "forksCount")));
    }

    @Test
    @DisplayName("Should return empty list if no stored repositories match criteria")
    void getStoredRepositories_noMatchingResults() {
        when(gitHubRepositoryRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(Collections.emptyList());

        List<GitHubRepository> result = gitHubService.getStoredRepositories("NonExistent", 9999, "stars").block();

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(gitHubRepositoryRepository, times(1)).findAll(any(Specification.class), any(Sort.class));
    }
}
