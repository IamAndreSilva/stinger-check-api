package com.stingercheck.votes;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException() {
        super("Vote rate limit exceeded");
    }
}
