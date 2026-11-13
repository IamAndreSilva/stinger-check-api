-- Looked up on every signed-in request; unique so a first-login race can't create duplicates.
CREATE UNIQUE INDEX uq_users_oauth_id ON users (oauth_id);

-- Account deletion anonymizes votes by user_id; uq_vote_user starts with tmdb_movie_id, so it can't serve that.
CREATE INDEX idx_votes_user_id ON votes (user_id);

-- The app writes these as UTC instants; store them with a time zone so the meaning can't drift.
ALTER TABLE users ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE votes ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
