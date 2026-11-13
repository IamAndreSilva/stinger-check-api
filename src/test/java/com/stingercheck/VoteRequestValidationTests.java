package com.stingercheck;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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
