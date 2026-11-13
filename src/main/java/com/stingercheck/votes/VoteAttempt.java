package com.stingercheck.votes;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * One accepted vote cast, kept for a couple of days so rate limits can't be reset by
 * retracting votes (retracted votes are deleted from the votes table).
 */
@Entity
@Table(name = "vote_attempts")
public class VoteAttempt {

    // First key of the per-IP advisory lock (see VoteAttemptRepository.lockIpAddress).
    public static final int LOCK_NAMESPACE = 1_727_384;

    // How long a cast waits for another cast from the same IP. Each one is short, so a longer
    // wait means a flood; giving up keeps one client from holding many pooled connections.
    static final String LOCK_TIMEOUT = "2s";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ip_address", nullable = false)
    private String ipAddress;

    @Column(name = "tmdb_movie_id", nullable = false)
    private Integer tmdbMovieId;

    // True when this cast created a new anonymous vote (not a change of an existing one).
    @Column(name = "new_anonymous_vote", nullable = false)
    private boolean newAnonymousVote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected VoteAttempt() {}

    public VoteAttempt(String ipAddress, Integer tmdbMovieId, boolean newAnonymousVote) {
        this.ipAddress = ipAddress;
        this.tmdbMovieId = tmdbMovieId;
        this.newAnonymousVote = newAnonymousVote;
    }

    public Long getId() { return id; }
}
