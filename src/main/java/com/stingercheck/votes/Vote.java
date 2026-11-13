package com.stingercheck.votes;

import jakarta.persistence.*;
import com.stingercheck.auth.User;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "votes")
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tmdb_movie_id", nullable = false)
    private Integer tmdbMovieId;

    @Column(name = "has_stinger", nullable = false)
    private Boolean hasStinger;

    @Column(name = "anonymous_token")
    private String anonymousToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    protected Vote() {}

    public Vote(Integer tmdbMovieId, Boolean hasStinger, String anonymousToken, User user, String ipAddress) {
        this.tmdbMovieId = tmdbMovieId;
        this.hasStinger = hasStinger;
        this.anonymousToken = anonymousToken;
        this.user = user;
        this.ipAddress = ipAddress;
    }

    public UUID getId() { return id; }
    public Integer getTmdbMovieId() { return tmdbMovieId; }
    public Boolean getHasStinger() { return hasStinger; }
    public String getAnonymousToken() { return anonymousToken; }
    public User getUser() { return user; }
    public String getIpAddress() { return ipAddress; }
    public Instant getCreatedAt() { return createdAt; }

    public void setHasStinger(Boolean hasStinger) { this.hasStinger = hasStinger; }
}
