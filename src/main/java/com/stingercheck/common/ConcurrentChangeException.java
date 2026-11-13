package com.stingercheck.common;

/** Two requests changed the same data at the same moment and a retry didn't resolve it; mapped to 409. */
public class ConcurrentChangeException extends RuntimeException {
    public ConcurrentChangeException(Throwable cause) {
        super("A conflicting change happened at the same time; please try again", cause);
    }
}
