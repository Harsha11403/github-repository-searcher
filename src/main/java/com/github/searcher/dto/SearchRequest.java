package com.github.searcher.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for searching GitHub repositories.")
public class SearchRequest {

    @NotBlank(message = "Query cannot be empty")
    @Schema(description = "The search query string for GitHub repositories", example = "spring-boot")
    private String query;

    @Schema(description = "Optional: Filter by programming language", example = "Java")
    private String language;

    @Schema(description = "Optional: Sort results by 'stars', 'forks', or 'updated'", example = "stars", allowableValues = {"stars", "forks", "updated"})
    private String sort;
}