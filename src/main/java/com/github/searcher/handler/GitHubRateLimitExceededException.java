package com.github.searcher.handler;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS) 
public class GitHubRateLimitExceededException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	private final long retryAfterSeconds;

    public GitHubRateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
