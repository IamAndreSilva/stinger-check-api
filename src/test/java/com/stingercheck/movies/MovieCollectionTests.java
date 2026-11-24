package com.stingercheck.movies;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MovieCollectionTests {

    private static final TmdbMovieResult.CollectionRef SERIES = new TmdbMovieResult.CollectionRef(77, "Test Collection");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TmdbClient tmdbClient;

    @Test
    void seriesIsListedInReleaseOrder() throws Exception {
        when(tmdbClient.getMovie(999_021)).thenReturn(movieInSeries());
        when(tmdbClient.getCollection(77)).thenReturn(new TmdbCollection(77, "Test Collection", List.of(
                part(999_022, "Part Three", ""),
                part(999_021, "Part Two", "2024-03-01"),
                part(999_020, "Part One", "2021-10-22"))));

        mockMvc.perform(get("/api/movies/999021"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection.name").value("Test Collection"))
                .andExpect(jsonPath("$.collection.movies[0].title").value("Part One"))
                .andExpect(jsonPath("$.collection.movies[1].title").value("Part Two"))
                .andExpect(jsonPath("$.collection.movies[2].title").value("Part Three"))
                .andExpect(jsonPath("$.collection.movies[0].slug").value("part-one"))
                .andExpect(jsonPath("$.collection.movies[0].verdict").value("UNKNOWN"));
    }

    @Test
    void movieStillLoadsWhenTheSeriesCallFails() throws Exception {
        when(tmdbClient.getMovie(999_021)).thenReturn(movieInSeries());
        when(tmdbClient.getCollection(77)).thenThrow(new ResourceAccessException("timeout"));

        mockMvc.perform(get("/api/movies/999021"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Part Two"))
                .andExpect(jsonPath("$.collection").doesNotExist());
    }

    @Test
    void standaloneMovieHasNoSeries() throws Exception {
        when(tmdbClient.getMovie(999_023)).thenReturn(part(999_023, "Standalone", "2020-01-01"));

        mockMvc.perform(get("/api/movies/999023"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection").doesNotExist());
    }

    private static TmdbMovieResult movieInSeries() {
        return new TmdbMovieResult(999_021, "Part Two", "2024-03-01", null, null, null, 120, SERIES);
    }

    private static TmdbMovieResult part(int id, String title, String releaseDate) {
        return new TmdbMovieResult(id, title, releaseDate, null, null, null, null);
    }
}
