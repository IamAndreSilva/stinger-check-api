package com.stingercheck.movies;

import com.stingercheck.config.CacheNames;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MissingMoviesTest {

    private static final int MISSING_ID = 999_999_999;
    private static final int OTHER_ID = 1726;

    @Test
    void remembersMissingIds() {
        MissingMovies missingMovies = new MissingMovies(new ConcurrentMapCacheManager(CacheNames.TMDB_MOVIE_MISSING));

        missingMovies.remember(MISSING_ID);

        assertThat(missingMovies.isKnownMissing(MISSING_ID)).isTrue();
        assertThat(missingMovies.isKnownMissing(OTHER_ID)).isFalse();
    }

    @Test
    void aBrokenCacheMeansNotKnownMissing() {
        CacheManager broken = mock(CacheManager.class);
        when(broken.getCache(anyString())).thenThrow(new IllegalStateException("Redis is down"));
        MissingMovies missingMovies = new MissingMovies(broken);

        missingMovies.remember(MISSING_ID);

        assertThat(missingMovies.isKnownMissing(MISSING_ID)).isFalse();
    }
}
