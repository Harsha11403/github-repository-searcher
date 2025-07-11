package com.github.searcher.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.github.searcher.dto.SearchRequest;
import com.github.searcher.model.GitHubRepository;
import com.github.searcher.service.GitHubService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/github")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "GitHub Repository Search", description = "API for searching and managing GitHub repositories")
public class GitHubController {

    private final GitHubService gitHubService;

    @Operation(summary = "Search GitHub repositories and save/update them",
               description = "Searches GitHub for repositories based on provided criteria and stores the results in the database.")
    @ApiResponse(responseCode = "200", description = "Repositories fetched and saved successfully",
                 content = @Content(mediaType = "application/json",
                 schema = @Schema(example = "{\"message\": \"Repositories fetched and saved successfully\", \"repositories\": [...]}")))
    @ApiResponse(responseCode = "400", description = "Invalid search request",
                 content = @Content(mediaType = "application/json",
                 schema = @Schema(example = "{\"message\": \"Validation error\", \"errors\": {\"query\": \"must not be blank\"}}")))
    @ApiResponse(responseCode = "500", description = "Internal server error",
                 content = @Content(mediaType = "application/json",
                 schema = @Schema(example = "{\"message\": \"An unexpected error occurred\"}")))
    @PostMapping("/search")
    public Mono<ResponseEntity<Map<String, Object>>> searchGitHubRepositories(@Valid @RequestBody SearchRequest searchRequest) {
        log.info("Received search request: {}", searchRequest);
        return gitHubService.searchAndSaveRepositories(searchRequest)
                .map(repositories -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("message", "Repositories fetched and saved successfully");
                    response.put("repositories", repositories);
                    return ResponseEntity.ok(response);
                });
    }

    @Operation(summary = "Get stored GitHub repositories",
               description = "Retrieves a list of GitHub repositories already stored in the database, with optional filtering and sorting.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved stored repositories",
                 content = @Content(mediaType = "application/json",
                 schema = @Schema(implementation = GitHubRepository.class)))
    @ApiResponse(responseCode = "500", description = "Internal server error",
                 content = @Content(mediaType = "application/json",
                 schema = @Schema(example = "{\"message\": \"An unexpected error occurred\"}")))
    @GetMapping("/repositories")
    public Mono<ResponseEntity<List<GitHubRepository>>> getStoredRepositories(
            @Parameter(description = "Filter repositories by programming language")
            @RequestParam(required = false) String language,
            @Parameter(description = "Filter repositories by minimum number of stars")
            @RequestParam(required = false) Integer minStars,
            @Parameter(description = "Sort repositories by a specific field (e.g., 'stars', 'forks')",
                       schema = @Schema(type = "string", allowableValues = {"stars", "forks", "updated"}))
            @RequestParam(required = false) String sort) {
        log.info("Received request to get stored repositories with language: {}, minStars: {}, sort: {}", language, minStars, sort);
        return gitHubService.getStoredRepositories(language, minStars, sort)
                .map(ResponseEntity::ok);
    }
}
