package com.stingercheck.votes;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoteRepository extends JpaRepository<Vote, UUID> {

    Optional<Vote> findByTmdbMovieIdAndAnonymousToken(Integer tmdbMovieId, String anonymousToken);

    Optional<Vote> findByTmdbMovieIdAndUserId(Integer tmdbMovieId, UUID userId);

    boolean existsByTmdbMovieIdAndAnonymousToken(Integer tmdbMovieId, String anonymousToken);

    @Query("SELECT v.tmdbMovieId FROM Vote v GROUP BY v.tmdbMovieId ORDER BY MAX(v.createdAt) DESC")
    List<Integer> findRecentlyVotedMovieIds(Limit limit);

    /** Rows of [tmdbMovieId, yesCount, noCount] for the given movies that have votes. */
    @Query("SELECT v.tmdbMovieId, SUM(CASE WHEN v.hasStinger = true THEN 1 ELSE 0 END), SUM(CASE WHEN v.hasStinger = false THEN 1 ELSE 0 END) FROM Vote v WHERE v.tmdbMovieId IN :ids GROUP BY v.tmdbMovieId")
    List<Object[]> countVotesByMovieIds(@Param("ids") List<Integer> tmdbMovieIds);

    // Insert-or-update in one statement, so two concurrent casts (a double click) can't
    // both insert and hit the unique constraint.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO votes (id, tmdb_movie_id, has_stinger, anonymous_token, ip_address, created_at)
            VALUES (gen_random_uuid(), :movieId, :hasStinger, :token, :ip, now())
            ON CONFLICT (tmdb_movie_id, anonymous_token) DO UPDATE SET has_stinger = EXCLUDED.has_stinger
            """, nativeQuery = true)
    int upsertAnonymousVote(@Param("movieId") Integer tmdbMovieId, @Param("token") String anonymousToken,
                            @Param("hasStinger") boolean hasStinger, @Param("ip") String ipAddress);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO votes (id, tmdb_movie_id, has_stinger, user_id, ip_address, created_at)
            VALUES (gen_random_uuid(), :movieId, :hasStinger, :userId, :ip, now())
            ON CONFLICT (tmdb_movie_id, user_id) DO UPDATE SET has_stinger = EXCLUDED.has_stinger
            """, nativeQuery = true)
    int upsertUserVote(@Param("movieId") Integer tmdbMovieId, @Param("userId") UUID userId,
                       @Param("hasStinger") boolean hasStinger, @Param("ip") String ipAddress);

    // Moves a browser's anonymous votes to the signed-in user, except for movies the user
    // already voted on (their signed-in vote wins; see deleteAnonymousVotes).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE votes v SET user_id = :userId, anonymous_token = NULL
            WHERE v.anonymous_token = :token
              AND NOT EXISTS (SELECT 1 FROM votes mine WHERE mine.user_id = :userId AND mine.tmdb_movie_id = v.tmdb_movie_id)
            """, nativeQuery = true)
    int claimAnonymousVotes(@Param("token") String anonymousToken, @Param("userId") UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Vote v WHERE v.anonymousToken = :token")
    int deleteAnonymousVotes(@Param("token") String anonymousToken);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Vote v SET v.ipAddress = NULL WHERE v.ipAddress IS NOT NULL AND v.createdAt < :cutoff")
    int clearIpAddressesOlderThan(@Param("cutoff") Instant cutoff);

    // Detaches a user's votes so they still count but can no longer be linked to anyone:
    // the account link and IP are removed and each vote gets a token nobody holds.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE votes SET user_id = NULL, ip_address = NULL, "
            + "anonymous_token = CAST(gen_random_uuid() AS VARCHAR) WHERE user_id = :userId",
            nativeQuery = true)
    int anonymizeByUserId(@Param("userId") UUID userId);
}
