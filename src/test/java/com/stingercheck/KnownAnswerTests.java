package com.stingercheck;

import com.stingercheck.answers.KnownAnswer;
import com.stingercheck.answers.KnownAnswerImportJob;
import com.stingercheck.answers.KnownAnswerRepository;
import com.stingercheck.movies.TmdbClient;
import com.stingercheck.movies.TmdbMovieResult;
import com.stingercheck.votes.VoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Transactional: the tests share the local database, so every change (imports included) is rolled back.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class KnownAnswerTests {

    private static final int MOVIE = 999_030;
    private static final int STALE = 999_039;
    private static final int AVENGERS_ENDGAME = 299_534;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private KnownAnswerRepository knownAnswerRepository;

    @Autowired
    private KnownAnswerImportJob importJob;

    @MockitoBean
    private TmdbClient tmdbClient;

    @BeforeEach
    void movieExists() {
        when(tmdbClient.getMovie(anyInt())).thenAnswer(call ->
                new TmdbMovieResult(call.getArgument(0), "Test Movie", "2008-04-30", null, null, null, null));
    }

    @Test
    void knownAnswerNeedsNoVotes() throws Exception {
        knownAnswer(MOVIE, "TMDB", true, null);
        when(tmdbClient.search("test")).thenReturn(List.of(tmdbMovie(MOVIE)));

        mockMvc.perform(get("/api/movies/" + MOVIE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("YES"))
                .andExpect(jsonPath("$.votes.knownAnswer").value(true))
                .andExpect(jsonPath("$.votes.total").value(0));

        mockMvc.perform(get("/api/movies/search").param("q", "test"))
                .andExpect(jsonPath("$[0].verdict").value("YES"));
    }

    @Test
    void knownAnswerWinsOverVotes() throws Exception {
        knownAnswer(MOVIE, "TMDB", true, null);
        IntStream.range(0, 3).forEach(i -> vote(false, null));

        mockMvc.perform(get("/api/movies/" + MOVIE))
                .andExpect(jsonPath("$.verdict").value("YES"))
                .andExpect(jsonPath("$.votes.no").value(3));
    }

    @Test
    void handWrittenAnswerBeatsTmdb() throws Exception {
        knownAnswer(MOVIE, "TMDB", true, null);
        knownAnswer(MOVIE, "EDITOR", false, null);
        vote(true, 2);

        mockMvc.perform(get("/api/movies/" + MOVIE))
                .andExpect(jsonPath("$.verdict").value("NO"))
                .andExpect(jsonPath("$.votes.sceneCount").doesNotExist());
    }

    @Test
    void handWrittenSceneCountBeatsVoters() throws Exception {
        knownAnswer(MOVIE, "EDITOR", true, 3);
        vote(true, 1);

        mockMvc.perform(get("/api/movies/" + MOVIE))
                .andExpect(jsonPath("$.votes.sceneCount").value(3));
    }

    @Test
    void votersGiveTheCountWhenTheKnownAnswerHasNone() throws Exception {
        knownAnswer(MOVIE, "TMDB", true, null);
        vote(true, 2);
        vote(true, 2);
        vote(true, 2);

        mockMvc.perform(get("/api/movies/" + MOVIE))
                .andExpect(jsonPath("$.verdict").value("YES"))
                .andExpect(jsonPath("$.votes.sceneCount").value(2));
    }

    @Test
    void moviesWithoutAKnownAnswerStillUseVotes() throws Exception {
        mockMvc.perform(get("/api/movies/" + MOVIE))
                .andExpect(jsonPath("$.verdict").value("UNKNOWN"))
                .andExpect(jsonPath("$.votes.knownAnswer").value(false));
    }

    @Test
    void importReplacesTmdbAnswersAndKeepsHandWrittenOnes() {
        knownAnswer(STALE, "TMDB", true, null);
        knownAnswer(MOVIE, "EDITOR", false, null);
        when(tmdbClient.discoverCreditStingers(1)).thenReturn(page(900_000, 60, 2));
        when(tmdbClient.discoverCreditStingers(2)).thenReturn(page(900_060, 60, 2));

        assertThat(importJob.importFromTmdb()).isEqualTo(120);

        assertThat(tmdbAnswerIds()).hasSize(120).contains(900_000, 900_119).doesNotContain(STALE);
        assertThat(knownAnswerRepository.findFor(List.of(MOVIE))).containsEntry(MOVIE, new KnownAnswer(false, null));
    }

    @Test
    void importKeepsAnswersWhenTmdbListsTooFewFilms() {
        knownAnswer(STALE, "TMDB", true, null);
        when(tmdbClient.discoverCreditStingers(1)).thenReturn(page(900_000, 5, 1));

        assertThat(importJob.importFromTmdb()).isZero();

        assertThat(tmdbAnswerIds()).contains(STALE);
    }

    @Test
    void importKeepsAnswersWhenAPageFails() {
        knownAnswer(STALE, "TMDB", true, null);
        when(tmdbClient.discoverCreditStingers(1)).thenReturn(page(900_000, 60, 2));
        when(tmdbClient.discoverCreditStingers(2)).thenThrow(new ResourceAccessException("timeout"));

        assertThatThrownBy(importJob::importFromTmdb).isInstanceOf(ResourceAccessException.class);
        importJob.importOnSchedule();

        assertThat(tmdbAnswerIds()).contains(STALE).doesNotContain(900_000);
    }

    @Test
    void handWrittenListIsLoaded() {
        assertThat(knownAnswerRepository.findFor(List.of(AVENGERS_ENDGAME)))
                .containsEntry(AVENGERS_ENDGAME, new KnownAnswer(false, null));
    }

    private void knownAnswer(int tmdbMovieId, String source, boolean hasStinger, Integer sceneCount) {
        jdbcClient.sql("""
                        INSERT INTO known_answers (tmdb_movie_id, source, has_stinger, scene_count)
                        VALUES (:id, :source, :hasStinger, :sceneCount)
                        ON CONFLICT (tmdb_movie_id, source) DO UPDATE
                        SET has_stinger = EXCLUDED.has_stinger, scene_count = EXCLUDED.scene_count
                        """)
                .param("id", tmdbMovieId)
                .param("source", source)
                .param("hasStinger", hasStinger)
                .param("sceneCount", sceneCount)
                .update();
    }

    // Stored directly, so the per-IP vote limits don't get in the way.
    private void vote(boolean hasStinger, Integer sceneCount) {
        String token = UUID.randomUUID().toString();
        voteRepository.upsertAnonymousVote(MOVIE, token, hasStinger, null);
        jdbcClient.sql("UPDATE votes SET scene_count = :count WHERE anonymous_token = :token")
                .param("count", sceneCount)
                .param("token", token)
                .update();
    }

    private List<Integer> tmdbAnswerIds() {
        return jdbcClient.sql("SELECT tmdb_movie_id FROM known_answers WHERE source = 'TMDB'")
                .query(Integer.class)
                .list();
    }

    private static TmdbMovieResult.SearchResponse page(int firstId, int size, int totalPages) {
        return new TmdbMovieResult.SearchResponse(
                IntStream.range(firstId, firstId + size).mapToObj(KnownAnswerTests::tmdbMovie).toList(), totalPages);
    }

    private static TmdbMovieResult tmdbMovie(int id) {
        return new TmdbMovieResult(id, "Test Movie", "2008-04-30", null, null, null, null);
    }
}
