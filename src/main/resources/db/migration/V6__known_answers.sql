-- Answers known without votes: imported from TMDB's credit-scene keywords (source 'TMDB')
-- or written by hand in R__editor_known_answers.sql (source 'EDITOR', which wins).
-- Each source owns its rows, so the import and the hand-written list never overwrite each other.
CREATE TABLE known_answers (
    tmdb_movie_id INTEGER     NOT NULL,
    source        VARCHAR(16) NOT NULL,
    has_stinger   BOOLEAN     NOT NULL,
    scene_count   INTEGER,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tmdb_movie_id, source),
    CONSTRAINT chk_known_answer_source CHECK (source IN ('TMDB', 'EDITOR')),
    CONSTRAINT chk_known_answer_scene_count CHECK (scene_count BETWEEN 1 AND 4),
    CONSTRAINT chk_known_answer_scene_count_needs_stinger CHECK (has_stinger OR scene_count IS NULL)
);
