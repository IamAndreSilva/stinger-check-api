package com.stingercheck.config;

/** Redis cache names, shared by the @Cacheable call sites and the TTLs in RedisConfig. */
public final class CacheNames {

    public static final String TMDB_SEARCH = "tmdb-search";
    public static final String TMDB_MOVIE = "tmdb-movie";
    public static final String TMDB_NOW_PLAYING = "tmdb-now-playing";
    public static final String TMDB_MOVIE_MISSING = "tmdb-movie-missing";
    public static final String SITEMAP = "sitemap";

    private CacheNames() {}
}
