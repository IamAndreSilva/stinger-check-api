package com.stingercheck;

import com.stingercheck.movies.SitemapService;
import com.stingercheck.movies.TmdbClient;
import com.stingercheck.movies.TmdbMovieResult;
import com.stingercheck.votes.Vote;
import com.stingercheck.votes.VoteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SitemapEndpointTests {

    private static final String SITE_URL = "http://localhost:5173";
    private static final int VOTED_MOVIE_ID = 999_101;
    private static final int NOW_PLAYING_MOVIE_ID = 999_102;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private TmdbClient tmdbClient;

    // These caches live in Redis: clear before so real local data can't leak in,
    // and after so this test's fake movies don't end up in the local dev sitemap.
    @BeforeEach
    @AfterEach
    void clearCaches() {
        Stream.of(SitemapService.CACHE_NAME, "tmdb-movie", "tmdb-now-playing")
                .map(cacheManager::getCache)
                .forEach(Cache::clear);
    }

    @Test
    void listsStaticPagesVotedAndNowPlayingMoviesOnce() throws Exception {
        TmdbMovieResult voted = movie(VOTED_MOVIE_ID, "Iron Man");
        TmdbMovieResult nowPlaying = movie(NOW_PLAYING_MOVIE_ID, "Amélie");
        voteRepository.saveAndFlush(new Vote(VOTED_MOVIE_ID, true, UUID.randomUUID().toString(), null, null));
        when(tmdbClient.getMovie(VOTED_MOVIE_ID)).thenReturn(voted);
        when(tmdbClient.nowPlaying()).thenReturn(List.of(voted, nowPlaying));

        String xml = mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andReturn().getResponse().getContentAsString();

        assertThat(xml)
                .contains("<loc>" + SITE_URL + "/</loc>")
                .contains("<loc>" + SITE_URL + "/privacy</loc>")
                .contains("<loc>" + SITE_URL + "/movies/" + NOW_PLAYING_MOVIE_ID + "-amelie</loc>");
        String votedUrl = "<loc>" + SITE_URL + "/movies/" + VOTED_MOVIE_ID + "-iron-man</loc>";
        assertThat(xml.split(votedUrl, -1)).hasSize(2);
    }

    private static TmdbMovieResult movie(int id, String title) {
        return new TmdbMovieResult(id, title, "2008-05-02", null, null, null, null);
    }
}
