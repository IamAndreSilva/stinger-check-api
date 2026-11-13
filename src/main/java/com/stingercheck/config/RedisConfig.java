package com.stingercheck.config;

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    // Cached values carry their Java class name; bump this when a cached type is renamed or
    // changed incompatibly, so old entries are ignored instead of failing to deserialize.
    private static final String CACHE_KEY_VERSION = "v1:";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith(CACHE_KEY_VERSION)
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base.entryTtl(Duration.ofHours(1)))
                .withInitialCacheConfigurations(Map.of(
                        CacheNames.TMDB_SEARCH, base.entryTtl(Duration.ofMinutes(15)),
                        CacheNames.TMDB_MOVIE, base.entryTtl(Duration.ofHours(6)),
                        CacheNames.TMDB_NOW_PLAYING, base.entryTtl(Duration.ofHours(6)),
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
