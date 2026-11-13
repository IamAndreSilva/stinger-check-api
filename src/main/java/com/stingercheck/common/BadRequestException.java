package com.stingercheck.common;

/** A client error whose message is safe to show to the caller; mapped to 400. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
