package com.stingercheck.config;

import com.stingercheck.movies.TmdbCacheTtl;
import com.stingercheck.movies.TmdbCollection;
import com.stingercheck.movies.TmdbMovieResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    // Bump this when a cached type changes incompatibly, so old entries are ignored instead of
    // failing to deserialize. v3: each cache stores plain JSON of its own type (no class names).
    static final String CACHE_KEY_VERSION = "v3:";

    /**
     * Production gives the cache its own Redis (allkeys-lru), so a flood of new cache entries
     * evicts only cache entries and never login sessions. Locally both use the same Redis.
     */
    @Bean(destroyMethod = "close")
    public CacheRedisConnection cacheRedisConnection(@Value("${app.cache.redis.host}") String host,
                                                     @Value("${app.cache.redis.port}") int port) {
        return new CacheRedisConnection(host, port);
    }

    /**
     * Each cache reads and writes JSON of the one type it holds. Nothing in Redis names a Java
     * class, so an entry can't make the app instantiate an arbitrary class, and any list
     * (List.of() included) reads back.
     */
    @Bean
    public RedisCacheManager cacheManager(CacheRedisConnection cacheRedisConnection) {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        JavaType movies = jsonMapper.getTypeFactory().constructCollectionType(List.class, TmdbMovieResult.class);
        Clock clock = Clock.systemUTC();

        // Movies and series released over 90 days ago are kept for a week (TmdbCacheTtl).
        return RedisCacheManager.builder(cacheRedisConnection.connectionFactory())
                .cacheDefaults(cacheOf(new StringRedisSerializer()).entryTtl(Duration.ofHours(1)))
                .withInitialCacheConfigurations(Map.of(
                        CacheNames.TMDB_SEARCH, cacheOf(json(jsonMapper, movies)).entryTtl(Duration.ofMinutes(15)),
                        CacheNames.TMDB_MOVIE, cacheOf(json(jsonMapper, TmdbMovieResult.class))
                                .entryTtl(new TmdbCacheTtl(Duration.ofHours(6), clock)),
                        CacheNames.TMDB_NOW_PLAYING, cacheOf(json(jsonMapper, movies)).entryTtl(Duration.ofHours(6)),
                        CacheNames.TMDB_COLLECTION, cacheOf(json(jsonMapper, TmdbCollection.class))
                                .entryTtl(new TmdbCacheTtl(Duration.ofHours(24), clock)),
                        CacheNames.TMDB_MOVIE_MISSING, cacheOf(json(jsonMapper, Boolean.class)).entryTtl(Duration.ofHours(1)),
                        CacheNames.SITEMAP, cacheOf(new StringRedisSerializer()).entryTtl(Duration.ofHours(1))
                ))
                // Every cache needs its type above; an unlisted name fails instead of guessing one.
                .disableCreateOnMissingCache()
                .build();
    }

    // The cache is an optimisation: if Redis is down or an entry can't be read, log it and
    // run the method instead of failing the request.
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    private static RedisCacheConfiguration cacheOf(RedisSerializer<?> serializer) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith(CACHE_KEY_VERSION)
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .disableCachingNullValues();
    }

    private static JacksonJsonRedisSerializer<Object> json(JsonMapper jsonMapper, JavaType type) {
        return new JacksonJsonRedisSerializer<>(jsonMapper, type);
    }

    private static <T> JacksonJsonRedisSerializer<T> json(JsonMapper jsonMapper, Class<T> type) {
        return new JacksonJsonRedisSerializer<>(jsonMapper, type);
    }
}
