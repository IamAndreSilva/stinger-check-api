package com.stingercheck.movies;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TmdbClientTest {

    private static final int MISSING_ID = 999_999_999;

    // No HTTP call: the base URL and key are never used, so this fails fast without a network.
    @Test
    void knownMissingMovieSkipsTheTmdbCall() {
        MissingMovies missingMovies = mock(MissingMovies.class);
        when(missingMovies.isKnownMissing(MISSING_ID)).thenReturn(true);
        TmdbClient client = new TmdbClient("unused-key", "https://images.invalid/", "https://images.invalid/", missingMovies);

        assertThatThrownBy(() -> client.getMovie(MISSING_ID)).isInstanceOf(MovieNotFoundException.class);
    }
}
