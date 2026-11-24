package com.stingercheck.movies;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TmdbCacheTtlTest {

    private static final Duration RECENT_TTL = Duration.ofHours(6);
    private static final Duration SETTLED_TTL = Duration.ofDays(7);
    private static final int KEY = 1726;

    // "Today" is 2026-11-24, so films released before 2026-08-26 are settled.
    private final TmdbCacheTtl ttl = new TmdbCacheTtl(RECENT_TTL,
            Clock.fixed(Instant.parse("2026-11-24T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void olderFilmIsKeptForAWeek() {
        assertThat(ttl.getTimeToLive(KEY, movie("2008-04-30"))).isEqualTo(SETTLED_TTL);
    }

    @Test
    void recentFilmIsRefetchedSooner() {
        assertThat(ttl.getTimeToLive(KEY, movie("2026-11-01"))).isEqualTo(RECENT_TTL);
        assertThat(ttl.getTimeToLive(KEY, movie("2026-08-26"))).isEqualTo(RECENT_TTL);
    }

    @Test
    void upcomingFilmIsRefetchedSooner() {
        assertThat(ttl.getTimeToLive(KEY, movie("2027-05-01"))).isEqualTo(RECENT_TTL);
    }

    @Test
    void filmWithoutAUsableReleaseDateIsRefetchedSooner() {
        assertThat(ttl.getTimeToLive(KEY, movie(null))).isEqualTo(RECENT_TTL);
        assertThat(ttl.getTimeToLive(KEY, movie(""))).isEqualTo(RECENT_TTL);
        assertThat(ttl.getTimeToLive(KEY, movie("2008"))).isEqualTo(RECENT_TTL);
    }

    @Test
    void collectionOfOlderFilmsIsKeptForAWeek() {
        TmdbCollection collection = collection(movie("2008-04-30"), movie("2010-04-28"));

        assertThat(ttl.getTimeToLive(KEY, collection)).isEqualTo(SETTLED_TTL);
    }

    @Test
    void collectionWithANewOrUnannouncedPartIsRefetchedSooner() {
        assertThat(ttl.getTimeToLive(KEY, collection(movie("2008-04-30"), movie("2026-11-01")))).isEqualTo(RECENT_TTL);
        assertThat(ttl.getTimeToLive(KEY, collection(movie("2008-04-30"), movie("")))).isEqualTo(RECENT_TTL);
        assertThat(ttl.getTimeToLive(KEY, collection())).isEqualTo(RECENT_TTL);
    }

    @Test
    void anythingElseIsRefetchedSooner() {
        assertThat(ttl.getTimeToLive(KEY, "something else")).isEqualTo(RECENT_TTL);
        assertThat(ttl.getTimeToLive(KEY, null)).isEqualTo(RECENT_TTL);
    }

    private static TmdbMovieResult movie(String releaseDate) {
        return new TmdbMovieResult(KEY, "Iron Man", releaseDate, null, null, null, null);
    }

    private static TmdbCollection collection(TmdbMovieResult... parts) {
        return new TmdbCollection(131292, "Iron Man Collection", List.of(parts));
    }
}
