package com.stingercheck.votes;

import com.stingercheck.common.BadRequestException;

import java.util.UUID;

/** The X-Anonymous-Token a browser generates once and sends with its anonymous votes. */
public final class AnonymousTokens {

    private AnonymousTokens() {}

    public static void validate(String token) {
        if (token == null || token.isBlank()) {
            throw new BadRequestException("Anonymous token required");
        }

        try {
            UUID.fromString(token);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid anonymous token format");
        }
    }
}
