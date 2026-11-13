package com.stingercheck;

import com.stingercheck.movies.MovieNotFoundException;
import com.stingercheck.movies.TmdbClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API errors keep their real status code instead of turning into a redirect to the login page. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiErrorHandlingTests {

    private static final int UNKNOWN_MOVIE_ID = 999_201;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TmdbClient tmdbClient;

    @Test
    void unknownMovieIs404() throws Exception {
        when(tmdbClient.getMovie(anyInt())).thenThrow(new MovieNotFoundException(UNKNOWN_MOVIE_ID));

        mockMvc.perform(get("/api/movies/" + UNKNOWN_MOVIE_ID))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void votingOnUnknownMovieIs404() throws Exception {
        when(tmdbClient.getMovie(anyInt())).thenThrow(new MovieNotFoundException(UNKNOWN_MOVIE_ID));

        mockMvc.perform(post("/api/movies/" + UNKNOWN_MOVIE_ID + "/votes")
                        .header("X-Anonymous-Token", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hasStinger\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void tmdbOutageIs502() throws Exception {
        when(tmdbClient.nowPlaying()).thenThrow(new ResourceAccessException("timed out"));

        mockMvc.perform(get("/api/movies/now-playing"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void unknownApiPathIs401NotARedirect() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void meIs204WhenSignedOut() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }
}
