package com.stingercheck.config;

import com.stingercheck.movies.TmdbCollection;
import com.stingercheck.movies.TmdbMovieResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnection;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Every cached type comes back from Redis equal to what went in. */
@SpringBootTest
class CacheRoundTripTests {

    private static final String KEY = "round-trip-test";
    private static final Duration WRITE_WAIT = Duration.ofSeconds(2);

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CacheRedisConnection cacheRedisConnection;

    @AfterEach
    void deleteEntries() {
        try (RedisConnection connection = cacheRedisConnection.connectionFactory().getConnection()) {
            for (String cacheName : List.of(CacheNames.TMDB_SEARCH, CacheNames.TMDB_MOVIE, CacheNames.TMDB_NOW_PLAYING,
                    CacheNames.TMDB_COLLECTION, CacheNames.TMDB_MOVIE_MISSING, CacheNames.SITEMAP)) {
                connection.keyCommands().del((RedisConfig.CACHE_KEY_VERSION + cacheName + "::" + KEY).getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    // TmdbClient caches List.of() when TMDB's answer has no results.
    @Test
    void emptySearchResult() throws InterruptedException {
        assertRoundTrip(CacheNames.TMDB_SEARCH, List.of());
    }

    @Test
    void searchResults() throws InterruptedException {
        assertRoundTrip(CacheNames.TMDB_SEARCH, new ArrayList<>(List.of(movie(1), movie(2))));
    }

    @Test
    void immutableSearchResult() throws InterruptedException {
        assertRoundTrip(CacheNames.TMDB_SEARCH, List.of(movie(1)));
    }

    @Test
    void movieInASeries() throws InterruptedException {
        TmdbMovieResult movie = new TmdbMovieResult(438_631, "Dune", "2021-09-15", "/poster.jpg", "/backdrop.jpg",
                "Paul Atreides...", 155, new TmdbMovieResult.CollectionRef(726_871, "Dune Collection"));

        assertRoundTrip(CacheNames.TMDB_MOVIE, movie);
    }

    @Test
    void nowPlaying() throws InterruptedException {
        assertRoundTrip(CacheNames.TMDB_NOW_PLAYING, List.of(movie(1), movie(2), movie(3)));
    }

    @Test
    void collection() throws InterruptedException {
        assertRoundTrip(CacheNames.TMDB_COLLECTION, new TmdbCollection(726_871, "Dune Collection", List.of(movie(1), movie(2))));
    }

    @Test
    void missingMovieMarker() throws InterruptedException {
        assertRoundTrip(CacheNames.TMDB_MOVIE_MISSING, Boolean.TRUE);
    }

    @Test
    void sitemap() throws InterruptedException {
        assertRoundTrip(CacheNames.SITEMAP, "<?xml version=\"1.0\"?><urlset><url><loc>https://stingercheck.com/</loc></url></urlset>");
    }

    private void assertRoundTrip(String cacheName, Object value) throws InterruptedException {
        Cache cache = cacheManager.getCache(cacheName);
        cache.put(KEY, value);

        assertThat(readBack(cache)).isEqualTo(value);
    }

    // Spring Data Redis may store cache entries asynchronously, so put() can return before the
    // entry is in Redis: wait briefly for it to appear.
    private static Object readBack(Cache cache) throws InterruptedException {
        long deadline = System.nanoTime() + WRITE_WAIT.toNanos();
        Cache.ValueWrapper entry = cache.get(KEY);
        while (entry == null && System.nanoTime() < deadline) {
            Thread.sleep(10);
            entry = cache.get(KEY);
        }
        assertThat(entry).as("cache entry").isNotNull();
        return entry.get();
    }

    private static TmdbMovieResult movie(int id) {
        return new TmdbMovieResult(id, "Movie " + id, "2024-01-01", "/poster-" + id + ".jpg", null, "Overview", 120);
    }
}
