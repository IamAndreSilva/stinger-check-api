package com.stingercheck;

import com.stingercheck.movies.TmdbClient;
import com.stingercheck.movies.TmdbMovieResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Not @Transactional: the requests must really commit to race each other. */
@SpringBootTest
@AutoConfigureMockMvc
class ConcurrentVoteTests {

    private static final int MOVIE_ID = 999_301;
    private static final int OK = 200;
    private static final int TOO_MANY_REQUESTS = 429;

    // More than the per-movie limit of 2, so a race would show up as extra accepted votes.
    private static final int PARALLEL_NEW_VOTES = 5;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private TmdbClient tmdbClient;

    @BeforeEach
    void movieExists() {
        when(tmdbClient.getMovie(anyInt())).thenReturn(new TmdbMovieResult(MOVIE_ID, "Test Movie", "2024-01-01", null, null, null, null));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM votes WHERE tmdb_movie_id = ?", MOVIE_ID);
        jdbcTemplate.update("DELETE FROM vote_attempts WHERE tmdb_movie_id = ?", MOVIE_ID);
    }

    @Test
    void doubleClickStoresOneVoteAndCountsOnce() throws Exception {
        String token = UUID.randomUUID().toString();

        List<Integer> statuses = castConcurrently(2, () -> token);

        assertThat(statuses).containsOnly(OK);
        assertThat(count("SELECT COUNT(*) FROM votes WHERE tmdb_movie_id = ? AND anonymous_token = ?", MOVIE_ID, token))
                .isEqualTo(1);
        // Only the first click created a vote, so the second person on this connection can still vote.
        assertThat(count("SELECT COUNT(*) FROM vote_attempts WHERE tmdb_movie_id = ? AND new_anonymous_vote", MOVIE_ID))
                .isEqualTo(1);
    }

    @Test
    void parallelNewTokensCantGetPastThePerMovieLimit() throws Exception {
        List<Integer> statuses = castConcurrently(PARALLEL_NEW_VOTES, () -> UUID.randomUUID().toString());

        assertThat(statuses).filteredOn(status -> status == OK).hasSize(2);
        assertThat(statuses).filteredOn(status -> status == TOO_MANY_REQUESTS).hasSize(PARALLEL_NEW_VOTES - 2);
        assertThat(count("SELECT COUNT(*) FROM votes WHERE tmdb_movie_id = ?", MOVIE_ID)).isEqualTo(2);
    }

    private List<Integer> castConcurrently(int requests, Supplier<String> tokens) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(requests)) {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                String token = tokens.get();
                Callable<Integer> castVote = () -> {
                    start.await();
                    return mockMvc.perform(post("/api/movies/" + MOVIE_ID + "/votes")
                                    .header("X-Anonymous-Token", token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"hasStinger\":true}"))
                            .andReturn().getResponse().getStatus();
                };
                results.add(executor.submit(castVote));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
        }

        return statuses;
    }

    private Integer count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }
}
