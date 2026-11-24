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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VoteSceneCountTests {

    private static final String VOTE_URL = "/api/movies/999010/votes";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TmdbClient tmdbClient;

    private final String token = UUID.randomUUID().toString();

    @BeforeEach
    void movieExists() {
        when(tmdbClient.getMovie(anyInt())).thenReturn(new TmdbMovieResult(999_010, "Test Movie", "2024-01-01", null, null, null, null));
    }

    @Test
    void yesVoterCanAddACount() throws Exception {
        castVote(token, true).andExpect(status().isOk());

        setCount(token, "3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userSceneCount").value(3))
                .andExpect(jsonPath("$.sceneCount").value(3));
    }

    @Test
    void nullClearsTheCount() throws Exception {
        castVote(token, true);
        setCount(token, "2");

        setCount(token, "null")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userSceneCount").doesNotExist())
                .andExpect(jsonPath("$.sceneCount").doesNotExist());
    }

    @Test
    void mostCommonCountLeads() throws Exception {
        // Two voters: the most new anonymous votes one IP may cast on a movie per day.
        String other = UUID.randomUUID().toString();
        castVote(token, true).andExpect(status().isOk());
        castVote(other, true).andExpect(status().isOk());
        setCount(token, "3").andExpect(status().isOk());

        // Tied: no leading count.
        setCount(other, "2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sceneCount").doesNotExist());

        setCount(other, "3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sceneCount").value(3));
    }

    @Test
    void countNeedsAYesVote() throws Exception {
        setCount(token, "1").andExpect(status().isBadRequest());

        castVote(token, false);
        setCount(token, "1").andExpect(status().isBadRequest());
    }

    @Test
    void switchingToNoClearsTheCount() throws Exception {
        castVote(token, true);
        setCount(token, "2");

        castVote(token, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userSceneCount").doesNotExist());

        castVote(token, true)
                .andExpect(jsonPath("$.userSceneCount").doesNotExist())
                .andExpect(jsonPath("$.sceneCount").doesNotExist());
    }

    @Test
    void outOfRangeCountsAreRejected() throws Exception {
        castVote(token, true);
        setCount(token, "0").andExpect(status().isBadRequest());
        setCount(token, "5").andExpect(status().isBadRequest());
    }

    private ResultActions castVote(String anonymousToken, boolean hasStinger) throws Exception {
        return mockMvc.perform(post(VOTE_URL)
                .header("X-Anonymous-Token", anonymousToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hasStinger\":" + hasStinger + "}"));
    }

    private ResultActions setCount(String anonymousToken, String sceneCount) throws Exception {
        return mockMvc.perform(put(VOTE_URL + "/scene-count")
                .header("X-Anonymous-Token", anonymousToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sceneCount\":" + sceneCount + "}"));
    }
}
