package com.stingercheck.answers;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Repository
public class KnownAnswerRepository {

    private static final String TMDB = "TMDB";

    private final JdbcClient jdbcClient;

    public KnownAnswerRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** Known answers by movie id; a hand-written (EDITOR) answer beats TMDB's for the same movie. */
    @Transactional(readOnly = true)
    public Map<Integer, KnownAnswer> findFor(Collection<Integer> tmdbMovieIds) {
        Map<Integer, KnownAnswer> answers = new HashMap<>();

        if (tmdbMovieIds.isEmpty()) {
            return answers;
        }

        jdbcClient.sql("""
                        SELECT DISTINCT ON (tmdb_movie_id) tmdb_movie_id, has_stinger, scene_count
                        FROM known_answers
                        WHERE tmdb_movie_id IN (:ids)
                        ORDER BY tmdb_movie_id, (source = 'EDITOR') DESC
                        """)
                .param("ids", tmdbMovieIds)
                .query((rs, rowNum) -> {
                    answers.put(rs.getInt("tmdb_movie_id"),
                            new KnownAnswer(rs.getBoolean("has_stinger"), rs.getObject("scene_count", Integer.class)));
                    return null;
                })
                .list();

        return answers;
    }

    /** Swaps all TMDB answers for these "yes" movies in one transaction; EDITOR rows are left alone. */
    @Transactional
    public void replaceTmdbAnswers(Collection<Integer> tmdbMovieIds) {
        jdbcClient.sql("DELETE FROM known_answers WHERE source = :source").param("source", TMDB).update();

        for (Integer tmdbMovieId : tmdbMovieIds) {
            jdbcClient.sql("""
                            INSERT INTO known_answers (tmdb_movie_id, source, has_stinger)
                            VALUES (:id, :source, true)
                            ON CONFLICT DO NOTHING
                            """)
                    .param("id", tmdbMovieId)
                    .param("source", TMDB)
                    .update();
        }
    }
}
