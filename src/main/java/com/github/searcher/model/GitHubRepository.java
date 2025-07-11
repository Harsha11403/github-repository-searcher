package com.github.searcher.model;

import java.time.OffsetDateTime;
import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name="github_repositories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Represents a GitHub repository, either fetched from the API or stored locally.")
public class GitHubRepository {

    @Id
    @Column(name = "id")
    @Schema(description = "Unique ID of the GitHub repository (from GitHub API)", example = "123456789")
    private Long id;

    @Column(name = "name", nullable = false)
    @Schema(description = "Name of the repository", example = "spring-boot-starter-webflux")
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    @Schema(description = "Description of the repository", example = "Spring Boot Starter for building reactive web applications using Spring WebFlux.")
    private String description;

    @Column(name = "owner_name", nullable = false)
    @Schema(description = "Username of the repository owner", example = "spring-projects")
    private String ownerName;

    @Column(name = "language")
    @Schema(description = "Primary programming language of the repository", example = "Java")
    private String language;

    @Column(name = "stars_count")
    @Schema(description = "Number of stars the repository has received", example = "75000")
    private Integer starsCount;

    @Column(name = "forks_count")
    @Schema(description = "Number of times the repository has been forked", example = "20000")
    private Integer forksCount;

    @Column(name = "last_updated", nullable = false)
    @Schema(description = "Date and time when the repository was last updated (ISO 8601 format)", example = "2024-07-09T14:30:00Z")
    private OffsetDateTime lastUpdated;
    

    public void updateFrom(GitHubRepository other) {
        this.description = other.description;
        this.ownerName = other.ownerName;
        this.language = other.language;
        this.starsCount = other.starsCount;
        this.forksCount = other.forksCount;
        this.lastUpdated = other.lastUpdated;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GitHubRepository that = (GitHubRepository) o;
        return Objects.equals(id, that.id) &&
               Objects.equals(name, that.name) &&
               Objects.equals(description, that.description) &&
               Objects.equals(ownerName, that.ownerName) &&
               Objects.equals(language, that.language) &&
               Objects.equals(starsCount, that.starsCount) &&
               Objects.equals(forksCount, that.forksCount) &&
               Objects.equals(lastUpdated, that.lastUpdated);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, ownerName, language, starsCount, forksCount, lastUpdated);
    }
    
}
