package com.github.searcher.handler;

import com.github.searcher.controller.test.ExceptionThrowingController;
import com.github.searcher.dto.SearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.WebFluxConfigurer;

class GlobalExceptionHandlerTest {

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        ApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class);

        this.webTestClient = WebTestClient.bindToApplicationContext(context).build();
    }

    @Configuration
    @EnableWebFlux
    static class TestConfig implements WebFluxConfigurer {
        @org.springframework.context.annotation.Bean
        public ExceptionThrowingController exceptionThrowingController() {
            return new ExceptionThrowingController();
        }

        @org.springframework.context.annotation.Bean
        public GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }
    }

    @Test
    @DisplayName("Should handle GitHubApiException and return custom error response with correct status")
    void handleGitHubApiException() {
        webTestClient.get().uri("/test-exceptions/github-api-error?statusCode=422&message=GitHub API client error: Missing 'q' parameter.")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.error").isEqualTo("GitHub API Error")
                .jsonPath("$.message").isEqualTo("GitHub API client error: Missing 'q' parameter.");
    }

    @Test
    @DisplayName("Should handle GitHubRateLimitExceededException and return 429 TOO_MANY_REQUESTS")
    void handleGitHubRateLimitExceededException() {
        webTestClient.get().uri("/test-exceptions/rate-limit-error?retryAfter=120")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.error").isEqualTo("GitHub API Rate Limit Exceeded")
                .jsonPath("$.message").isEqualTo("GitHub API rate limit exceeded. Please try again later.");
    }

    @Test
    @DisplayName("Should handle MethodArgumentNotValidException and return 400 BAD_REQUEST for validation errors")
    void handleValidationExceptions() {
        SearchRequest invalidRequest = new SearchRequest("", null, null);

        webTestClient.post().uri("/test-exceptions/validation-error")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.query").isEqualTo("Query cannot be empty");
    }

    @Test
    @DisplayName("Should handle generic RuntimeException and return 500 INTERNAL_SERVER_ERROR")
    void handleGenericRuntimeException() {
        webTestClient.get().uri("/test-exceptions/runtime-error")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.error").isEqualTo("Internal Server Error")
                .jsonPath("$.message").isEqualTo("An unexpected error occurred: Something unexpected happened!");
    }

    @Test
    @DisplayName("Should handle ResponseStatusException and return specified status and reason")
    void handleResponseStatusException() {
        webTestClient.get().uri("/test-exceptions/response-status-error?statusCode=404&reason=Resource Not Found")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.error").isEqualTo("404 NOT_FOUND")
                .jsonPath("$.message").isEqualTo("Resource Not Found");
    }
}
