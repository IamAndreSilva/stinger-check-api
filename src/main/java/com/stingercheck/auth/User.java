package com.stingercheck.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    private String name;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "oauth_id", nullable = false)
    private String oauthId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    protected User() {}

    public User(String email, String name, String avatarUrl, String oauthId) {
        this.email = email;
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.oauthId = oauthId;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getOauthId() { return oauthId; }
    public Instant getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
}
