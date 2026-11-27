package com.stingercheck;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/** A sign-in that fails or is cancelled at Google goes back to the site, not to a missing login page. */
@SpringBootTest
@AutoConfigureMockMvc
class SignInFailureTests {

    private static final String FRONTEND_URL = "http://localhost:5173";
    private static final String CALLBACK_URL = "/api/login/oauth2/code/google";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void cancellingAtGoogleReturnsToThePageTheSignInStartedFrom() throws Exception {
        MvcResult signInStart = mockMvc.perform(get("/api/oauth2/authorization/google")
                        .param("returnTo", "/movies/1726-iron-man"))
                .andReturn();
        Cookie session = signInStart.getResponse().getCookie("SESSION");
        String state = UriComponentsBuilder.fromUriString(signInStart.getResponse().getRedirectedUrl())
                .build().getQueryParams().getFirst("state");
        assertThat(session).isNotNull();

        // What Google sends back when the user presses Cancel.
        mockMvc.perform(get(CALLBACK_URL).cookie(session).param("error", "access_denied").param("state", state))
                .andExpect(redirectedUrl(FRONTEND_URL + "/movies/1726-iron-man"));
    }

    @Test
    void callbackWithoutASignInInProgressGoesToTheHomePage() throws Exception {
        mockMvc.perform(get(CALLBACK_URL).param("error", "access_denied").param("state", "made-up"))
                .andExpect(redirectedUrl(FRONTEND_URL + "/"));
    }
}
