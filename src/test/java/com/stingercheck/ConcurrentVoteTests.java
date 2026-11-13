package com.stingercheck;

import com.stingercheck.movies.TmdbClient;
import com.stingercheck.movies.TmdbMovieResult;
import org.junit.jupiter.api.AfterEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Not @Transactional: the requests must really commit to race each other. */
@SpringBootTest
@AutoConfigureMockMvc
class ConcurrentVoteTests {

    private static final int MOVIE_ID = 999_301;
    private static final int CONCURRENT_REQUESTS = 2;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private TmdbClient tmdbClient;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM votes WHERE tmdb_movie_id = ?", MOVIE_ID);
        jdbcTemplate.update("DELETE FROM vote_attempts WHERE tmdb_movie_id = ?", MOVIE_ID);
    }

    @Test
    void doubleClickStoresOneVoteWithoutErrors() throws Exception {
        when(tmdbClient.getMovie(anyInt())).thenReturn(new TmdbMovieResult(MOVIE_ID, "Test Movie", "2024-01-01", null, null, null, null));
        String token = UUID.randomUUID().toString();
        CountDownLatch start = new CountDownLatch(1);

        Callable<Integer> castVote = () -> {
            start.await();
            return mockMvc.perform(post("/api/movies/" + MOVIE_ID + "/votes")
                            .header("X-Anonymous-Token", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"hasStinger\":true}"))
                    .andReturn().getResponse().getStatus();
        };

        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)) {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                results.add(executor.submit(castVote));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
        }

        assertThat(statuses).containsOnly(200);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM votes WHERE tmdb_movie_id = ? AND anonymous_token = ?", Integer.class, MOVIE_ID, token))
                .isEqualTo(1);
    }
}
