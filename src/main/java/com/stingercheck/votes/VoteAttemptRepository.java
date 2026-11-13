package com.stingercheck.votes;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface VoteAttemptRepository extends JpaRepository<VoteAttempt, Long> {

    long countByIpAddressAndCreatedAtAfter(String ipAddress, Instant since);

    long countByIpAddressAndTmdbMovieIdAndNewAnonymousVoteTrueAndCreatedAtAfter(
            String ipAddress, Integer tmdbMovieId, Instant since);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM VoteAttempt a WHERE a.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
