package com.stingercheck;

import com.stingercheck.movies.TmdbClient;
import com.stingercheck.movies.TmdbMovieResult;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VoteRequestValidationTests {

    private static final String VOTE_URL = "/api/movies/999003/votes";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TmdbClient tmdbClient;

    // Votes are only accepted for movies TMDB knows.
    @BeforeEach
    void movieExists() {
        when(tmdbClient.getMovie(anyInt())).thenReturn(new TmdbMovieResult(999_002, "Test Movie", "2024-01-01", null, null, null, null));
    }

    @Test
    void voteIsRecorded() throws Exception {
        castVote("{\"hasStinger\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userVote").value(true));
    }

    @Test
    void emptyBodyIsRejected() throws Exception {
        castVote("{}").andExpect(status().isBadRequest());
    }

    @Test
    void preRenameFieldIsRejected() throws Exception {
        castVote("{\"hasPcs\":true}").andExpect(status().isBadRequest());
    }

    private ResultActions castVote(String body) throws Exception {
        return mockMvc.perform(post(VOTE_URL)
                .header("X-Anonymous-Token", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
