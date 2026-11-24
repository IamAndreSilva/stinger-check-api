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
                .andExpect(jsonPath("$.userSceneCount").value(3));
    }

    @Test
    void oneAnswerIsNotShownAsTheCount() throws Exception {
        castVote(token, true);

        setCount(token, "3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sceneCount").doesNotExist());
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
    void countIsShownOnceThreeVotersAgree() throws Exception {
        String[] voters = votersFromTwoAddresses();
        setCount(voters[0], "3");
        setCount(voters[1], "3").andExpect(jsonPath("$.sceneCount").doesNotExist());

        setCount(voters[2], "3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sceneCount").value(3));
    }

    @Test
    void countNeedsAClearMajority() throws Exception {
        String[] voters = votersFromTwoAddresses();
        setCount(voters[0], "1");
        setCount(voters[1], "2");
        setCount(voters[2], "3").andExpect(jsonPath("$.sceneCount").doesNotExist());

        setCount(voters[2], "2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sceneCount").value(2));
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

    // Three "yes" voters; one IP may only add two new anonymous votes to a movie per day.
    private String[] votersFromTwoAddresses() throws Exception {
        String[] voters = {token, UUID.randomUUID().toString(), UUID.randomUUID().toString()};
        castVote(voters[0], true).andExpect(status().isOk());
        castVote(voters[1], true).andExpect(status().isOk());
        castVote(voters[2], true, "10.0.0.2").andExpect(status().isOk());
        return voters;
    }

    private ResultActions castVote(String anonymousToken, boolean hasStinger) throws Exception {
        return castVote(anonymousToken, hasStinger, "127.0.0.1");
    }

    private ResultActions castVote(String anonymousToken, boolean hasStinger, String ipAddress) throws Exception {
        return mockMvc.perform(post(VOTE_URL)
                .with(request -> {
                    request.setRemoteAddr(ipAddress);
                    return request;
                })
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
