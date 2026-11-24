package com.stingercheck.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    // Cached values carry their Java class name; bump this when a cached type is renamed or
    // changed incompatibly, so old entries are ignored instead of failing to deserialize.
    private static final String CACHE_KEY_VERSION = "v2:";

    /**
     * Production gives the cache its own Redis (allkeys-lru), so a flood of new cache entries
     * evicts only cache entries and never login sessions. Locally both use the same Redis.
     */
    @Bean(destroyMethod = "close")
    public CacheRedisConnection cacheRedisConnection(@Value("${app.cache.redis.host}") String host,
                                                     @Value("${app.cache.redis.port}") int port) {
        return new CacheRedisConnection(host, port);
    }

    @Bean
    public RedisCacheManager cacheManager(CacheRedisConnection cacheRedisConnection) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith(CACHE_KEY_VERSION)
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        return RedisCacheManager.builder(cacheRedisConnection.connectionFactory())
                .cacheDefaults(base.entryTtl(Duration.ofHours(1)))
                .withInitialCacheConfigurations(Map.of(
                        CacheNames.TMDB_SEARCH, base.entryTtl(Duration.ofMinutes(15)),
                        CacheNames.TMDB_MOVIE, base.entryTtl(Duration.ofHours(6)),
                        CacheNames.TMDB_NOW_PLAYING, base.entryTtl(Duration.ofHours(6)),
                        CacheNames.TMDB_COLLECTION, base.entryTtl(Duration.ofHours(24)),
                        CacheNames.TMDB_MOVIE_MISSING, base.entryTtl(Duration.ofHours(1)),
                        CacheNames.SITEMAP, base.entryTtl(Duration.ofHours(1))
                ))
                .build();
    }

    // The cache is an optimisation: if Redis is down or an entry can't be read, log it and
    // run the method instead of failing the request.
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }
}
