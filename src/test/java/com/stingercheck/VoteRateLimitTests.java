package com.stingercheck;

import com.stingercheck.movies.TmdbClient;
import com.stingercheck.movies.TmdbMovieResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** One connection can't decide a movie's verdict on its own by generating anonymous tokens. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VoteRateLimitTests {

    private static final int MOVIE_ID = 999_401;
    private static final String VOTE_URL = "/api/movies/" + MOVIE_ID + "/votes";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TmdbClient tmdbClient;

    @BeforeEach
    void movieExists() {
        when(tmdbClient.getMovie(anyInt())).thenReturn(new TmdbMovieResult(MOVIE_ID, "Test Movie", "2024-01-01", null, null, null, null));
    }

    @Test
    void thirdNewAnonymousVoteFromSameConnectionIsRejected() throws Exception {
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();

        vote(first, true).andExpect(status().isOk());
        vote(second, true).andExpect(status().isOk());
        vote(UUID.randomUUID().toString(), true).andExpect(status().isTooManyRequests());

        // Changing an existing vote is still allowed.
        vote(first, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userVote").value(false));
    }

    @Test
    void retractingDoesNotFreeUpTheLimit() throws Exception {
        String token = UUID.randomUUID().toString();

        vote(token, true).andExpect(status().isOk());
        mockMvc.perform(delete(VOTE_URL).header("X-Anonymous-Token", token)).andExpect(status().isOk());
        vote(token, true).andExpect(status().isOk());

        vote(UUID.randomUUID().toString(), true).andExpect(status().isTooManyRequests());
    }

    @Test
    void voteResponseCarriesTheVerdict() throws Exception {
        vote(UUID.randomUUID().toString(), true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("UNKNOWN"))
                .andExpect(jsonPath("$.total").value(1));
    }

    private ResultActions vote(String token, boolean hasStinger) throws Exception {
        return mockMvc.perform(post(VOTE_URL)
                .header("X-Anonymous-Token", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hasStinger\":" + hasStinger + "}"));
    }
}
