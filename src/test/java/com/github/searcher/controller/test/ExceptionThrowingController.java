package com.github.searcher.controller.test;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.github.searcher.dto.SearchRequest;
import com.github.searcher.handler.GitHubApiException;
import com.github.searcher.handler.GitHubRateLimitExceededException;

import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/test-exceptions")
public class ExceptionThrowingController {

    @GetMapping("/github-api-error")
    public Mono<String> triggerGitHubApiError(@RequestParam HttpStatus status, @RequestParam String message) {
        return Mono.error(new GitHubApiException(message, status));
    }

    @GetMapping("/rate-limit-error")
    public Mono<String> triggerRateLimitError(@RequestParam int retryAfter) {
        return Mono.error(new GitHubRateLimitExceededException("GitHub API rate limit exceeded. Please try again later.", retryAfter));
    }

    @PostMapping("/validation-error")
    public Mono<String> triggerValidationError(@Valid @RequestBody SearchRequest request) {
        return Mono.just("Valid request received.");
    }

    @GetMapping("/runtime-error")
    public Mono<String> triggerRuntimeException() {
        return Mono.error(new RuntimeException("Something unexpected happened!"));
    }

    @GetMapping("/response-status-error")
    public Mono<String> triggerResponseStatusException(@RequestParam HttpStatus status, @RequestParam String reason) {
        return Mono.error(new ResponseStatusException(status, reason));
    }
}
