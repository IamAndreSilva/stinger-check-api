-- How many extra scenes a "yes" voter counted; optional. 4 means "4 or more".
ALTER TABLE votes ADD COLUMN scene_count INTEGER;

ALTER TABLE votes ADD CONSTRAINT chk_vote_scene_count
    CHECK (scene_count BETWEEN 1 AND 4);

-- A count describes scenes, so a "no" vote can't carry one.
ALTER TABLE votes ADD CONSTRAINT chk_vote_scene_count_needs_stinger
    CHECK (has_stinger OR scene_count IS NULL);
