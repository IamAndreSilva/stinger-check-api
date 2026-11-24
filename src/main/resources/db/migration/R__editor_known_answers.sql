-- Hand-checked answers. They win over TMDB's tags and over votes, so only add films you're sure of.
-- Flyway re-runs this file whenever it changes: edit the list, restart, done.
--
-- One row per film: (TMDB id, 'EDITOR', has a scene after the credits, number of scenes or NULL).
-- The scene count is 1, 2, 3 or 4 (4 means "4 or more"), and must be NULL when there is no scene.
-- Find the id in the film's TMDB or Stinger Check URL, e.g. /movies/1726-iron-man is 1726.

DELETE FROM known_answers WHERE source = 'EDITOR';

INSERT INTO known_answers (tmdb_movie_id, source, has_stinger, scene_count) VALUES
    (299534, 'EDITOR', false, NULL)  -- Avengers: Endgame (2019)
;
