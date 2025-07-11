package com.github.searcher.service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.searcher.dto.SearchRequest;
import com.github.searcher.handler.GitHubApiException;
import com.github.searcher.handler.GitHubRateLimitExceededException;
import com.github.searcher.model.GitHubRepository;
import com.github.searcher.repository.GitHubRepositoryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubService {

    private final WebClient webClient;
    private final GitHubRepositoryRepository gitHubRepositoryRepository;

    @Value("${github.api.search.repositories.path:/search/repositories}")
    private String searchRepositoriesPath;

    public Mono<List<GitHubRepository>> searchAndSaveRepositories(SearchRequest searchRequest) {
        String query = searchRequest.getQuery();
        String language = searchRequest.getLanguage();
        String sort = searchRequest.getSort();

        StringBuilder uriBuilder = new StringBuilder(searchRepositoriesPath)
                .append("?q=").append(query);

        if (language != null && !language.isEmpty()) {
            uriBuilder.append("+language:").append(language);
        }
        if (sort != null && !sort.isEmpty()) {
            uriBuilder.append("&sort=").append(sort);
        }
        uriBuilder.append("&order=desc");

        String apiUrl = uriBuilder.toString();
        log.info("Attempting to fetch repositories from GitHub API using URL: {}", apiUrl);

        return webClient.get()
                .uri(apiUrl)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> {
                    Optional<String> rateLimitReset = clientResponse.headers().asHttpHeaders().containsKey("X-RateLimit-Reset") ?
                            Optional.ofNullable(clientResponse.headers().header("X-RateLimit-Reset").get(0)) : Optional.empty();

                    if (clientResponse.statusCode() == HttpStatus.FORBIDDEN && rateLimitReset.isPresent()) {
                        long resetTime = Long.parseLong(rateLimitReset.get());
                        long currentTime = OffsetDateTime.now().toEpochSecond();
                        int retryAfterSeconds = (int) Math.max(0, resetTime - currentTime);
                        log.error("GitHub API rate limit exceeded. Retry after {} seconds.", retryAfterSeconds);
                        return Mono.error(new GitHubRateLimitExceededException("GitHub API rate limit exceeded. Please try again later.", retryAfterSeconds));
                    } else {
                        return clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("GitHub API client error: {} Status: {}", errorBody, clientResponse.statusCode());
                                    return Mono.error(new GitHubApiException("GitHub API client error: " + errorBody, clientResponse.statusCode()));
                                });
                    }
                })
                .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("GitHub API server error: {} Status: {}", errorBody, clientResponse.statusCode());
                                    return Mono.error(new GitHubApiException("GitHub API server error: " + errorBody, clientResponse.statusCode()));
                                })
                )
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("An unexpected error occurred in WebClient reactive stream during GitHub API call: {} Status: {}", errorBody, clientResponse.statusCode());
                                    return Mono.error(new RuntimeException("An unexpected error occurred during GitHub API call: " + errorBody));
                                })
                )
                .bodyToMono(JsonNode.class)
                .onErrorMap(throwable -> {
                    if (throwable instanceof GitHubRateLimitExceededException || throwable instanceof GitHubApiException) {
                        return throwable;
                    }
                    log.error("An unexpected error occurred in WebClient reactive stream during GitHub API call: {}", throwable.getMessage(), throwable);
                    return new RuntimeException("Error fetching or saving repositories: " + throwable.getMessage(), throwable);
                })
                .flatMapMany(responseBody -> {
                    if (responseBody == null || !responseBody.has("items") || !responseBody.get("items").isArray()) {
                        log.warn("GitHub API response did not contain 'items' array or was null.");
                        return Flux.empty();
                    }
                    return Flux.fromIterable(StreamSupport.stream(responseBody.get("items").spliterator(), false)
                            .map(this::mapJsonNodeToGitHubRepository)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList()));
                })
                .flatMap(fetchedRepo ->
                    Mono.fromCallable(() -> {
                        Optional<GitHubRepository> existingRepoOptional = gitHubRepositoryRepository.findById(fetchedRepo.getId());
                        if (existingRepoOptional.isPresent()) {
                            GitHubRepository existingRepo = existingRepoOptional.get();
                            if (!existingRepo.equals(fetchedRepo)) {
                                existingRepo.updateFrom(fetchedRepo);
                                gitHubRepositoryRepository.save(existingRepo);
                                log.info("Updated existing repository: {}", existingRepo.getName());
                                return existingRepo;
                            } else {
                                log.info("Repository {} already exists and is up-to-date. No update needed.", existingRepo.getName());
                                return existingRepo;
                            }
                        } else {
                            gitHubRepositoryRepository.save(fetchedRepo);
                            log.info("Saved new repository: {}", fetchedRepo.getName());
                            return fetchedRepo;
                        }
                    }).subscribeOn(Schedulers.boundedElastic())
                )
                .collectList();
    }

    public Mono<List<GitHubRepository>> getStoredRepositories(String language, Integer minStars, String sort) {
        return Mono.fromCallable(() -> {
            Specification<GitHubRepository> spec = Specification.where(null);

            if (language != null && !language.isEmpty()) {
                spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("language")), language.toLowerCase()));
            }
            if (minStars != null) {
                spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("starsCount"), minStars));
            }

            Sort sortOrder = Sort.by(Sort.Direction.DESC, "starsCount");
            if (sort != null && !sort.isEmpty()) {
                switch (sort.toLowerCase()) {
                    case "forks":
                        sortOrder = Sort.by(Sort.Direction.DESC, "forksCount");
                        break;
                    case "lastupdated":
                        sortOrder = Sort.by(Sort.Direction.DESC, "lastUpdated");
                        break;
                    case "stars":
                    default:
                        sortOrder = Sort.by(Sort.Direction.DESC, "starsCount");
                        break;
                }
            }
            log.info("Retrieving stored repositories with filters: language='{}', minStars='{}', sort='{}'",
                    language != null ? language : "N/A", minStars != null ? minStars : "N/A", sort != null ? sort : "N/A");
            List<GitHubRepository> repositories = gitHubRepositoryRepository.findAll(spec, sortOrder);
            log.info("Found {} stored repositories matching criteria.", repositories.size());
            return repositories;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private GitHubRepository mapJsonNodeToGitHubRepository(JsonNode node) {
        if (node == null || !node.has("id") || !node.has("name") || !node.has("owner") || !node.get("owner").has("login")) {
            log.warn("Skipping repository due to missing essential fields: id, name, or owner. Node: {}", node);
            return null;
        }

        Long id = node.get("id").asLong();
        String name = node.get("name").asText();
        String description = node.has("description") && !node.get("description").isNull() ? node.get("description").asText() : null;
        String owner = node.get("owner").get("login").asText();
        String language = node.has("language") && !node.get("language").isNull() ? node.get("language").asText() : null;
        Integer stars = node.has("stargazers_count") ? node.get("stargazers_count").asInt() : 0;
        Integer forks = node.has("forks_count") ? node.get("forks_count").asInt() : 0;
        OffsetDateTime lastUpdated = node.has("updated_at") && !node.get("updated_at").isNull() ?
                OffsetDateTime.parse(node.get("updated_at").asText(), DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null;

        return new GitHubRepository(id, name, description, owner, language, stars, forks, lastUpdated);
    }
}
