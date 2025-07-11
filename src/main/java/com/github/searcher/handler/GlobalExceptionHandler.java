package com.github.searcher.handler;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ApiResponse(responseCode = "4XX", description = "GitHub API specific client error",
                 content = @Content(mediaType = "application/json",
                 schema = @Schema(implementation = Map.class),
                 examples = @ExampleObject(value = "{\"error\": \"GitHub API Error\", \"message\": \"Repository not found\"}")))
    @ApiResponse(responseCode = "5XX", description = "GitHub API specific server error",
                 content = @Content(mediaType = "application/json",
                 schema = @Schema(implementation = Map.class),
                 examples = @ExampleObject(value = "{\"error\": \"GitHub API Error\", \"message\": \"GitHub service unavailable\"}")))
    @ExceptionHandler(GitHubApiException.class)
    public ResponseEntity<Map<String, Object>> handleGitHubApiException(GitHubApiException ex) {
        log.error("GitHub API Exception caught: {}", ex.getMessage(), ex);
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("error", "GitHub API Error");
        errorDetails.put("message", ex.getMessage());
        return new ResponseEntity<>(errorDetails, ex.getStatusCode());
    }

    @ApiResponse(responseCode = "429", description = "GitHub API Rate Limit Exceeded",
                 content = @Content(mediaType = "application/json",
                 schema = @Schema(implementation = Map.class),
                 examples = @ExampleObject(value = "{\"error\": \"GitHub API Rate Limit Exceeded\", \"message\": \"You have exceeded your rate limit.\", \"retryAfterSeconds\": 3600}")))
    @ExceptionHandler(GitHubRateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleGitHubRateLimitExceededException(GitHubRateLimitExceededException ex) {
        log.warn("GitHub Rate Limit Exceeded Exception caught: {}", ex.getMessage(), ex);
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("error", "GitHub API Rate Limit Exceeded");
        errorDetails.put("message", ex.getMessage());
        errorDetails.put("retryAfterSeconds", ex.getRetryAfterSeconds());
        return new ResponseEntity<>(errorDetails, HttpStatus.TOO_MANY_REQUESTS);
    }

    @ApiResponse(responseCode = "400", description = "Validation error",
                 content = @Content(mediaType = "application/json",
                 schema = @Schema(implementation = Map.class),
                 examples = @ExampleObject(value = "{\"query\": \"must not be blank\", \"language\": \"size must be between 0 and 50\"}")))
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));
        log.warn("Validation Exception caught: {}", errors);
        return errors;
    }

    @ApiResponse(responseCode = "500", description = "Internal server error",
                 content = @Content(mediaType = "application/json",
                 schema = @Schema(implementation = Map.class),
                 examples = @ExampleObject(value = "{\"error\": \"Internal Server Error\", \"message\": \"An unexpected error occurred: NullPointerException\"}")))
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleGenericRuntimeException(RuntimeException ex) {
        log.error("Unhandled Runtime Exception caught: {}", ex.getMessage(), ex);
        Map<String, String> errorDetails = new HashMap<>();
        errorDetails.put("error", "Internal Server Error");
        errorDetails.put("message", "An unexpected error occurred: " + ex.getMessage());
        return errorDetails;
    }

    @ApiResponse(responseCode = "404", description = "Resource not found",
                 content = @Content(mediaType = "application/json",
                 schema = @Schema(implementation = Map.class),
                 examples = @ExampleObject(value = "{\"error\": \"NOT_FOUND\", \"message\": \"Resource not found\"}")))
    @ApiResponse(responseCode = "400", description = "Bad request",
                 content = @Content(mediaType = "application/json",
                 schema = @Schema(implementation = Map.class),
                 examples = @ExampleObject(value = "{\"error\": \"BAD_REQUEST\", \"message\": \"Invalid parameter\"}")))
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        log.warn("ResponseStatusException caught: {}", ex.getReason(), ex);
        Map<String, String> errorDetails = new HashMap<>();
        errorDetails.put("error", ex.getStatusCode().toString());
        errorDetails.put("message", ex.getReason());
        return new ResponseEntity<>(errorDetails, ex.getStatusCode());
    }
}
