package com.stingercheck;

import com.stingercheck.movies.MovieNotFoundException;
import com.stingercheck.movies.TmdbClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.ResourceAccessException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Which vote requests use up an hourly slot. Not @Transactional: the slot is recorded and
 * given back in separate transactions, which must really commit.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VoteAttemptAccountingTests {

    private static final int UNKNOWN_MOVIE_ID = 999_402;
    private static final int OUTAGE_MOVIE_ID = 999_403;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private TmdbClient tmdbClient;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM vote_attempts WHERE tmdb_movie_id IN (?, ?)", UNKNOWN_MOVIE_ID, OUTAGE_MOVIE_ID);
    }

    @Test
    void voteForUnknownMovieUsesAnHourlySlot() throws Exception {
        when(tmdbClient.getMovie(UNKNOWN_MOVIE_ID)).thenThrow(new MovieNotFoundException(UNKNOWN_MOVIE_ID));

        voteOn(UNKNOWN_MOVIE_ID).andExpect(status().isNotFound());

        assertThat(attemptsFor(UNKNOWN_MOVIE_ID)).isEqualTo(1);
    }

    @Test
    void tmdbOutageGivesTheSlotBack() throws Exception {
        when(tmdbClient.getMovie(OUTAGE_MOVIE_ID)).thenThrow(new ResourceAccessException("timed out"));

        voteOn(OUTAGE_MOVIE_ID).andExpect(status().isBadGateway());

        assertThat(attemptsFor(OUTAGE_MOVIE_ID)).isZero();
    }

    private Integer attemptsFor(int movieId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vote_attempts WHERE tmdb_movie_id = ?", Integer.class, movieId);
    }

    private ResultActions voteOn(int movieId) throws Exception {
        return mockMvc.perform(post("/api/movies/" + movieId + "/votes")
                .header("X-Anonymous-Token", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hasStinger\":true}"));
    }
}
