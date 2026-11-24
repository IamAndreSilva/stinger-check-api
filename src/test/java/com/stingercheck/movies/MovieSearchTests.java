package com.stingercheck.movies;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Each distinct search term costs a TMDB call and a cache entry, so terms are capped and normalised. */
@SpringBootTest
@AutoConfigureMockMvc
class MovieSearchTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TmdbClient tmdbClient;

    @Test
    void termsDifferingOnlyInCaseAndSpacesAreOneSearch() throws Exception {
        when(tmdbClient.search(anyString())).thenReturn(List.of());

        mockMvc.perform(get("/api/movies/search").param("q", "  Dune \t Part  TWO "))
                .andExpect(status().isOk());

        verify(tmdbClient).search("dune part two");
    }

    @Test
    void overlongTermIsRejectedWithoutCallingTmdb() throws Exception {
        String overlong = "a".repeat(MovieController.MAX_SEARCH_LENGTH + 1);

        mockMvc.perform(get("/api/movies/search").param("q", overlong))
                .andExpect(status().isBadRequest());

        verify(tmdbClient, never()).search(anyString());
    }

    @Test
    void blankTermFindsNothingWithoutCallingTmdb() throws Exception {
        mockMvc.perform(get("/api/movies/search").param("q", "   "))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(tmdbClient, never()).search(anyString());
    }
}
