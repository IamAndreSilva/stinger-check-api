-- Every accepted vote cast, so rate limits survive retracted (deleted) votes.
-- Rows are deleted after two days by the hourly cleanup job.
CREATE TABLE vote_attempts (
    id                 BIGSERIAL PRIMARY KEY,
    ip_address         VARCHAR(45) NOT NULL,
    tmdb_movie_id      INTEGER     NOT NULL,
    new_anonymous_vote BOOLEAN     NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_vote_attempts_ip_created ON vote_attempts (ip_address, created_at);
