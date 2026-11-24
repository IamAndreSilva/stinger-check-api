package com.stingercheck.movies;

import org.springframework.data.redis.cache.RedisCacheWriter;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * How long TMDB movie details and collections stay cached. Films released in the last few months,
 * and upcoming ones, still get new posters, runtimes and release dates, so they're refetched often.
 * Older films barely change, so they're kept for a week. TMDB's terms allow up to 6 months.
 */
public final class TmdbCacheTtl implements RedisCacheWriter.TtlFunction {

    static final Duration SETTLED_TTL = Duration.ofDays(7);
    static final int SETTLED_AFTER_DAYS = 90;

    private final Duration recentTtl;
    private final Clock clock;

    /** @param recentTtl how long to keep films that aren't settled yet */
    public TmdbCacheTtl(Duration recentTtl, Clock clock) {
        this.recentTtl = recentTtl;
        this.clock = clock;
    }

    @Override
    public Duration getTimeToLive(Object key, Object value) {
        return isSettled(value) ? SETTLED_TTL : recentTtl;
    }

    // A collection is settled once all its films are: a new or announced part keeps it fresh.
    private boolean isSettled(Object value) {
        return switch (value) {
            case TmdbMovieResult movie -> isSettled(movie);
            case TmdbCollection collection -> isSettled(collection.parts());
            case null, default -> false;
        };
    }

    private boolean isSettled(List<TmdbMovieResult> parts) {
        return !parts.isEmpty() && parts.stream().allMatch(this::isSettled);
    }

    // No release date (unannounced, or TMDB sends "") counts as recent.
    private boolean isSettled(TmdbMovieResult movie) {
        if (movie.releaseDate() == null || movie.releaseDate().isBlank()) {
            return false;
        }
        try {
            LocalDate released = LocalDate.parse(movie.releaseDate());
            return released.isBefore(LocalDate.now(clock).minusDays(SETTLED_AFTER_DAYS));
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
