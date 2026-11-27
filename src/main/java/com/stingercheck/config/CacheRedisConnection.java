package com.stingercheck.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

/**
 * The cache's own Redis connection, apart from the session store's.
 *
 * <p>Deliberately not a {@link RedisConnectionFactory} bean: Spring Boot would then stop
 * auto-configuring the one Spring Session uses. Connects lazily, so the app still starts
 * when the cache's Redis is down.
 */
public final class CacheRedisConnection implements AutoCloseable {

    // A cache miss only costs a TMDB call, so give up on a slow Redis quickly instead of
    // holding the request for Lettuce's defaults (10 s to connect, 60 s per command).
    private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(500);
    private static final Duration COMMAND_TIMEOUT = Duration.ofMillis(500);

    private final LettuceConnectionFactory connectionFactory;

    public CacheRedisConnection(String host, int port) {
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .commandTimeout(COMMAND_TIMEOUT)
                .clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder().connectTimeout(CONNECT_TIMEOUT).build())
                        .build())
                .build();
        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port), clientConfiguration);
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
