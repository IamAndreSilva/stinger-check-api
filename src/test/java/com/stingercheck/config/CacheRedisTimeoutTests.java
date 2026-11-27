package com.stingercheck.config;

import com.stingercheck.movies.MissingMovies;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A cache Redis that accepts connections but never answers must not hold up requests:
 * without timeouts each cache call would wait up to a minute.
 */
@SpringBootTest
class CacheRedisTimeoutTests {

    // Well under Lettuce's defaults (10 s connect, 60 s command), with room for a slow CI runner.
    private static final Duration MAX_CACHE_DELAY = Duration.ofSeconds(5);

    private static final int MOVIE_ID = 999_302;

    // The operating system completes the TCP handshake for a bound socket even though
    // nothing ever calls accept(), so the client connects and then hears nothing back.
    private static final ServerSocket SILENT_REDIS = openSilentSocket();

    @Autowired
    private MissingMovies missingMovies;

    @DynamicPropertySource
    static void cacheRedisNeverAnswers(DynamicPropertyRegistry registry) {
        registry.add("app.cache.redis.host", () -> "127.0.0.1");
        registry.add("app.cache.redis.port", SILENT_REDIS::getLocalPort);
    }

    @AfterAll
    static void closeSilentSocket() throws IOException {
        SILENT_REDIS.close();
    }

    @Test
    void unresponsiveCacheRedisFailsFast() {
        long start = System.nanoTime();

        boolean isKnownMissing = missingMovies.isKnownMissing(MOVIE_ID);

        assertThat(isKnownMissing).isFalse();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(MAX_CACHE_DELAY);
    }

    private static ServerSocket openSilentSocket() {
        try {
            return new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
