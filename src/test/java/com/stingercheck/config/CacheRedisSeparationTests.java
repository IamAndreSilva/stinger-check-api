package com.stingercheck.config;

import com.stingercheck.movies.MissingMovies;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cache uses its own Redis connection (app.cache.redis.*), apart from the session store's,
 * so in production a flood of cache entries can't evict sessions. Here the cache's Redis is
 * unreachable while the session store's runs.
 */
@SpringBootTest(properties = "app.cache.redis.port=" + CacheRedisSeparationTests.CLOSED_PORT)
class CacheRedisSeparationTests {

    // Nothing listens here, so connecting fails straight away.
    static final int CLOSED_PORT = 1;

    private static final int MOVIE_ID = 999_301;

    @Autowired
    private MissingMovies missingMovies;

    @Autowired
    private RedisConnectionFactory sessionConnectionFactory;

    @Test
    void cacheGoesToItsOwnRedis() {
        missingMovies.remember(MOVIE_ID);

        assertThat(missingMovies.isKnownMissing(MOVIE_ID)).isFalse();
    }

    @Test
    void sessionStoreKeepsWorkingWhenTheCacheRedisIsDown() {
        try (RedisConnection connection = sessionConnectionFactory.getConnection()) {
            assertThat(connection.ping()).isEqualTo("PONG");
        }
    }
}
