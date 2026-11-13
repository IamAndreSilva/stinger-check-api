package com.stingercheck.movies;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugsTest {

    @Test
    void lowercasesAndDashesWords() {
        assertThat(Slugs.of("Iron Man")).isEqualTo("iron-man");
    }

    @Test
    void collapsesPunctuationIntoSingleDashes() {
        assertThat(Slugs.of("Spider-Man: No Way Home")).isEqualTo("spider-man-no-way-home");
    }

    @Test
    void stripsAccents() {
        assertThat(Slugs.of("Amélie")).isEqualTo("amelie");
    }

    @Test
    void trimsLeadingAndTrailingDashes() {
        assertThat(Slugs.of("...And Justice for All!")).isEqualTo("and-justice-for-all");
    }

    @Test
    void returnsEmptyForTitlesWithoutLatinCharacters() {
        assertThat(Slugs.of("君の名は。")).isEmpty();
        assertThat(Slugs.of(null)).isEmpty();
    }

    @Test
    void moviePathFallsBackToIdWithoutSlug() {
        assertThat(Slugs.moviePath(1726, "Iron Man")).isEqualTo("/movies/1726-iron-man");
        assertThat(Slugs.moviePath(372058, "君の名は。")).isEqualTo("/movies/372058");
    }
}
