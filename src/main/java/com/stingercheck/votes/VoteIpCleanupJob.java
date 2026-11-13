package com.stingercheck.votes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * IP addresses are only needed for the hourly vote rate limit, so they are removed from
 * votes once they are older than the retention period. The votes themselves are kept.
 */
@Component
public class VoteIpCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(VoteIpCleanupJob.class);

    // The vote rate limit looks back one hour, so IPs must be kept for at least a day.
    private static final long MIN_RETENTION_DAYS = 1;

    private final VoteRepository voteRepository;
    private final long retentionDays;

    public VoteIpCleanupJob(VoteRepository voteRepository,
                            @Value("${app.vote-ip-retention-days}") long retentionDays) {
        if (retentionDays < MIN_RETENTION_DAYS) {
            throw new IllegalArgumentException(
                    "app.vote-ip-retention-days must be at least " + MIN_RETENTION_DAYS + ", was " + retentionDays);
        }

        this.voteRepository = voteRepository;
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

        return cleared;
    }
}
