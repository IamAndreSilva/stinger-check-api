package com.stingercheck.movies;

import com.stingercheck.config.CacheNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Remembers ids TMDB answered 404 for, so repeated requests for the same missing movie
 * don't each cost a TMDB call (@Cacheable never caches the exception).
 */
@Component
public class MissingMovies {

    private static final Logger log = LoggerFactory.getLogger(MissingMovies.class);

    private final CacheManager cacheManager;

    public MissingMovies(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    // Like the annotation-driven caches, a cache failure only costs the optimisation.
    public boolean isKnownMissing(int tmdbId) {
        try {
            Cache cache = cacheManager.getCache(CacheNames.TMDB_MOVIE_MISSING);
            return cache != null && cache.get(tmdbId) != null;
        } catch (RuntimeException e) {
            log.warn("Could not read missing-movie cache: {}", e.getMessage());
            return false;
        }
    }

    public void remember(int tmdbId) {
        try {
            Cache cache = cacheManager.getCache(CacheNames.TMDB_MOVIE_MISSING);
            if (cache != null) {
                cache.put(tmdbId, Boolean.TRUE);
            }
        } catch (RuntimeException e) {
            log.warn("Could not write missing-movie cache: {}", e.getMessage());
        }
    }
}
