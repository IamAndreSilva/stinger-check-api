package com.stingercheck.votes;

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

    @Query("SELECT DISTINCT v.tmdbMovieId FROM Vote v ORDER BY v.tmdbMovieId")
    List<Integer> findDistinctMovieIds();

    @Query("SELECT COUNT(v) FROM Vote v WHERE v.tmdbMovieId = :movieId AND v.hasStinger = true")
    long countYesByMovieId(@Param("movieId") Integer movieId);

    @Query("SELECT COUNT(v) FROM Vote v WHERE v.tmdbMovieId = :movieId AND v.hasStinger = false")
    long countNoByMovieId(@Param("movieId") Integer movieId);

    @Query("SELECT COUNT(v) FROM Vote v WHERE v.ipAddress = :ip AND v.createdAt > :since")
    long countByIpAddressSince(@Param("ip") String ipAddress, @Param("since") Instant since);

    @Query("SELECT v.tmdbMovieId, SUM(CASE WHEN v.hasStinger = true THEN 1 ELSE 0 END), SUM(CASE WHEN v.hasStinger = false THEN 1 ELSE 0 END) FROM Vote v WHERE v.tmdbMovieId IN :ids GROUP BY v.tmdbMovieId")
    List<Object[]> countVotesByMovieIds(@Param("ids") List<Integer> tmdbMovieIds);

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
