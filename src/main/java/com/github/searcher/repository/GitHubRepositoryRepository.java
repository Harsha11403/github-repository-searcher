package com.github.searcher.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.github.searcher.model.GitHubRepository;

@Repository
public interface GitHubRepositoryRepository extends JpaRepository<GitHubRepository, Long>, JpaSpecificationExecutor<GitHubRepository> {

}
