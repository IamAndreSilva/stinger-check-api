package com.stingercheck.config;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * The cache's own Redis connection, apart from the session store's.
 *
 * <p>Deliberately not a {@link RedisConnectionFactory} bean: Spring Boot would then stop
 * auto-configuring the one Spring Session uses. Connects lazily, so the app still starts
 * when the cache's Redis is down.
 */
public final class CacheRedisConnection implements AutoCloseable {

    private final LettuceConnectionFactory connectionFactory;

    public CacheRedisConnection(String host, int port) {
        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
    }

    public RedisConnectionFactory connectionFactory() {
        return connectionFactory;
    }

    @Override
    public void close() {
        connectionFactory.destroy();
    }
}
