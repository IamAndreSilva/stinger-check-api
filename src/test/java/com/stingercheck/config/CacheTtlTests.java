package com.stingercheck.config;

import com.stingercheck.movies.TmdbCollection;
import com.stingercheck.movies.TmdbMovieResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnection;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The TTLs RedisConfig gives TMDB entries, read back from Redis. */
@SpringBootTest
class CacheTtlTests {

    private static final int OLD_MOVIE_ID = 999_601;
    private static final int NEW_MOVIE_ID = 999_602;
    private static final int COLLECTION_ID = 999_603;

    // What Redis's TTL command answers for a key that doesn't exist.
    private static final long KEY_MISSING = -2;
    private static final Duration WRITE_WAIT = Duration.ofSeconds(2);

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CacheRedisConnection cacheRedisConnection;

    @AfterEach
    void deleteEntries() {
        try (RedisConnection connection = cacheRedisConnection.connectionFactory().getConnection()) {
            connection.keyCommands().del(key(CacheNames.TMDB_MOVIE, OLD_MOVIE_ID),
                    key(CacheNames.TMDB_MOVIE, NEW_MOVIE_ID), key(CacheNames.TMDB_COLLECTION, COLLECTION_ID));
        }
    }

    @Test
    void olderMovieIsCachedForAWeek() throws InterruptedException {
        cacheManager.getCache(CacheNames.TMDB_MOVIE).put(OLD_MOVIE_ID, movie(OLD_MOVIE_ID, "2008-04-30"));

        assertThat(ttlOf(CacheNames.TMDB_MOVIE, OLD_MOVIE_ID)).isBetween(Duration.ofDays(7).minusMinutes(1), Duration.ofDays(7));
    }

    @Test
    void newMovieIsCachedForSixHours() throws InterruptedException {
        String lastWeek = LocalDate.now().minusDays(7).toString();
        cacheManager.getCache(CacheNames.TMDB_MOVIE).put(NEW_MOVIE_ID, movie(NEW_MOVIE_ID, lastWeek));

        assertThat(ttlOf(CacheNames.TMDB_MOVIE, NEW_MOVIE_ID)).isBetween(Duration.ofHours(6).minusMinutes(1), Duration.ofHours(6));
    }

    @Test
    void collectionOfOlderMoviesIsCachedForAWeek() throws InterruptedException {
        TmdbCollection collection = new TmdbCollection(COLLECTION_ID, "Test Collection",
                List.of(movie(OLD_MOVIE_ID, "2008-04-30")));
        cacheManager.getCache(CacheNames.TMDB_COLLECTION).put(COLLECTION_ID, collection);

        assertThat(ttlOf(CacheNames.TMDB_COLLECTION, COLLECTION_ID)).isBetween(Duration.ofDays(7).minusMinutes(1), Duration.ofDays(7));
    }

    // Spring Data Redis may store cache entries asynchronously, so put() can return before the
    // entry is in Redis: wait briefly for it to appear.
    private Duration ttlOf(String cacheName, int id) throws InterruptedException {
        long deadline = System.nanoTime() + WRITE_WAIT.toNanos();
        try (RedisConnection connection = cacheRedisConnection.connectionFactory().getConnection()) {
            Long ttl = connection.keyCommands().ttl(key(cacheName, id));
            while (ttl != null && ttl == KEY_MISSING && System.nanoTime() < deadline) {
                Thread.sleep(10);
                ttl = connection.keyCommands().ttl(key(cacheName, id));
            }
            return Duration.ofSeconds(ttl);
        }
    }

    private static byte[] key(String cacheName, int id) {
        return ("v2:" + cacheName + "::" + id).getBytes(StandardCharsets.UTF_8);
    }

    private static TmdbMovieResult movie(int id, String releaseDate) {
        return new TmdbMovieResult(id, "Test movie", releaseDate, null, null, null, null);
    }
}
