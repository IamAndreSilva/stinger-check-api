package com.stingercheck;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sessions live in a capped Redis, so only starting a sign-in may create one. */
@SpringBootTest
@AutoConfigureMockMvc
class SessionCreationTests {

    private static final String SESSION_COOKIE = "SESSION";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedGetToAProtectedPathCreatesNoSession() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist(SESSION_COOKIE));
    }

    @Test
    void unauthenticatedPostToAProtectedPathCreatesNoSession() throws Exception {
        mockMvc.perform(post("/api/does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist(SESSION_COOKIE));
    }

    // Shows the check above can fail: the OAuth flow keeps its state in the session.
    @Test
    void startingASignInCreatesASession() throws Exception {
        mockMvc.perform(get("/api/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(cookie().exists(SESSION_COOKIE));
    }
}
