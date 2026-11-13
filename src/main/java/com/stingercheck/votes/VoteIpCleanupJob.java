package com.stingercheck.votes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * IP addresses are only needed for vote rate limiting, so they are removed from votes once
 * they are older than the retention period (the votes themselves are kept), and rate-limit
 * attempts are deleted once no limit looks at them any more.
 */
@Component
public class VoteIpCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(VoteIpCleanupJob.class);

    // The vote rate limits look back at most a day, so IPs must be kept for at least that long.
    private static final long MIN_RETENTION_DAYS = 1;

    // Longer than the longest rate-limit window (one day), with a margin.
    private static final Duration ATTEMPT_RETENTION = Duration.ofDays(2);

    private final VoteRepository voteRepository;
    private final VoteAttemptRepository voteAttemptRepository;
    private final long retentionDays;

    public VoteIpCleanupJob(VoteRepository voteRepository, VoteAttemptRepository voteAttemptRepository,
                            @Value("${app.vote-ip-retention-days}") long retentionDays) {
        if (retentionDays < MIN_RETENTION_DAYS) {
            throw new IllegalArgumentException(
                    "app.vote-ip-retention-days must be at least " + MIN_RETENTION_DAYS + ", was " + retentionDays);
        }

        this.voteRepository = voteRepository;
        this.voteAttemptRepository = voteAttemptRepository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${app.vote-ip-cleanup-cron}")
    @Transactional
    public int clearExpiredIpAddresses() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int cleared = voteRepository.clearIpAddressesOlderThan(cutoff);

        if (cleared > 0) {
            log.info("Cleared IP address from {} vote(s) older than {} day(s)", cleared, retentionDays);
        }

        int deletedAttempts = voteAttemptRepository.deleteOlderThan(Instant.now().minus(ATTEMPT_RETENTION));
        if (deletedAttempts > 0) {
            log.info("Deleted {} expired vote attempt(s)", deletedAttempts);
        }

        return cleared;
    }
}
