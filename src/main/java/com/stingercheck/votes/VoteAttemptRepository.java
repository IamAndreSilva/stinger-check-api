package com.stingercheck.votes;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface VoteAttemptRepository extends JpaRepository<VoteAttempt, Long> {

    /**
     * Serializes vote casts from one IP address until the current transaction ends, so
     * concurrent requests can't all read the same count and get past the rate limits together.
     * Uses the two-key lock form with its own first key, apart from Flyway's one-key locks.
     */
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(" + VoteAttempt.LOCK_NAMESPACE + ", hashtext(:ip))", nativeQuery = true)
    Integer lockIpAddress(@Param("ip") String ipAddress);

    /** Caps how long the current transaction waits for a lock (SQLState 55P03 when exceeded). */
    @Modifying
    @Query(value = "SET LOCAL lock_timeout = '" + VoteAttempt.LOCK_TIMEOUT + "'", nativeQuery = true)
    void limitLockWait();

    @Modifying(flushAutomatically = true)
    @Query("UPDATE VoteAttempt a SET a.newAnonymousVote = true WHERE a.id = :id")
    int markNewAnonymousVote(@Param("id") Long id);

    long countByIpAddressAndCreatedAtAfter(String ipAddress, Instant since);

    long countByIpAddressAndTmdbMovieIdAndNewAnonymousVoteTrueAndCreatedAtAfter(
            String ipAddress, Integer tmdbMovieId, Instant since);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM VoteAttempt a WHERE a.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
