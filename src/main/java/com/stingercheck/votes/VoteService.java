package com.stingercheck.votes;

import com.stingercheck.auth.User;
import com.stingercheck.common.SqlStates;
import com.stingercheck.auth.UserRepository;
import com.stingercheck.movies.TmdbClient;
import org.springframework.dao.DataAccessException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class VoteService {

    private static final int VOTES_PER_IP_PER_HOUR = 20;
    private static final Duration HOUR = Duration.ofHours(1);

    // Two, so a couple sharing a connection can both vote, while one person can't reach the
    // votes a verdict needs (StingerVerdict.MIN_VOTES) on their own by generating new tokens.
    static final int NEW_ANONYMOUS_VOTES_PER_IP_PER_MOVIE_PER_DAY = 2;
    private static final Duration DAY = Duration.ofDays(1);

    private final VoteRepository voteRepository;
    private final VoteAttemptRepository voteAttemptRepository;
    private final UserRepository userRepository;
    private final TmdbClient tmdbClient;
    private final TransactionTemplate transactionTemplate;

    public VoteService(VoteRepository voteRepository, VoteAttemptRepository voteAttemptRepository,
                       UserRepository userRepository, TmdbClient tmdbClient,
                       PlatformTransactionManager transactionManager) {
        this.voteRepository = voteRepository;
        this.voteAttemptRepository = voteAttemptRepository;
        this.userRepository = userRepository;
        this.tmdbClient = tmdbClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Runs in two short transactions with the TMDB lookup between them, so a slow TMDB
     * never holds a database connection.
     *
     * @throws com.stingercheck.movies.MovieNotFoundException when TMDB doesn't know the movie
     * @throws RateLimitExceededException when the IP address is over a vote limit
     */
    public VoteSummaryDto castVote(Integer tmdbMovieId, boolean hasStinger,
                                   String anonymousToken, OAuth2User oauthUser, String ipAddress) {
        User user = resolveUser(oauthUser);

        if (user == null) {
            AnonymousTokens.validate(anonymousToken);
        }

        // Counted before the TMDB lookup, so votes for made-up movie ids use up the hourly
        // limit too and can't make unlimited TMDB calls.
        Long attemptId = transactionTemplate.execute(status -> recordAttemptWithinHourlyLimit(ipAddress, tmdbMovieId));

        // Only real movies can be voted on; the lookup is cached.
        try {
            tmdbClient.getMovie(tmdbMovieId);
        } catch (RestClientException e) {
            // TMDB is down or slow (502): not the voter's fault, so give the hourly slot back.
            // If that fails too, keep the TMDB error as the one reported.
            try {
                releaseAttempt(attemptId);
            } catch (RuntimeException releaseError) {
                e.addSuppressed(releaseError);
            }
            throw e;
        }

        return transactionTemplate.execute(status ->
                storeVote(tmdbMovieId, hasStinger, anonymousToken, user, ipAddress, attemptId));
    }

    @Transactional
    public VoteSummaryDto retractVote(Integer tmdbMovieId, String anonymousToken,
                                      OAuth2User oauthUser) {
        User user = resolveUser(oauthUser);

        if (user != null) {
            voteRepository.findByTmdbMovieIdAndUserId(tmdbMovieId, user.getId())
                    .ifPresent(voteRepository::delete);
            return buildSummary(tmdbMovieId, null, user.getId());
        }

        AnonymousTokens.validate(anonymousToken);
        voteRepository.findByTmdbMovieIdAndAnonymousToken(tmdbMovieId, anonymousToken)
                .ifPresent(voteRepository::delete);
        return buildSummary(tmdbMovieId, anonymousToken, null);
    }

    @Transactional(readOnly = true)
    public VoteSummaryDto getSummary(Integer tmdbMovieId, String anonymousToken, OAuth2User oauthUser) {
        User user = resolveUser(oauthUser);
        UUID userId = null;

        if (user != null) {
            userId = user.getId();
        }

        return buildSummary(tmdbMovieId, anonymousToken, userId);
    }

    /** Verdict per movie id; movies without votes are UNKNOWN. */
    @Transactional(readOnly = true)
    public Map<Integer, StingerVerdict> verdictsFor(List<Integer> tmdbMovieIds) {
        Map<Integer, StingerVerdict> verdicts = new HashMap<>();

        if (tmdbMovieIds.isEmpty()) {
            return verdicts;
        }

        countVotes(tmdbMovieIds).forEach((id, counts) ->
                verdicts.put(id, StingerVerdict.fromVotes(counts.yes(), counts.no())));
        return verdicts;
    }

    /** @return the recorded attempt's id, or null when the IP address is unknown */
    private Long recordAttemptWithinHourlyLimit(String ipAddress, Integer tmdbMovieId) {
        if (ipAddress == null) {
            return null;
        }

        lockIpAddress(ipAddress);

        long recent = voteAttemptRepository.countByIpAddressAndCreatedAtAfter(ipAddress, Instant.now().minus(HOUR));
        if (recent >= VOTES_PER_IP_PER_HOUR) {
            throw new RateLimitExceededException();
        }

        return voteAttemptRepository.save(new VoteAttempt(ipAddress, tmdbMovieId, false)).getId();
    }

    private VoteSummaryDto storeVote(Integer tmdbMovieId, boolean hasStinger, String anonymousToken,
                                     User user, String ipAddress, Long attemptId) {
        if (user != null) {
            voteRepository.upsertUserVote(tmdbMovieId, user.getId(), hasStinger, ipAddress);
            return buildSummary(tmdbMovieId, null, user.getId());
        }

        // Held until commit: a concurrent cast from the same IP waits here, then sees this
        // vote, so a double click counts as one new vote and parallel requests can't all pass.
        if (ipAddress != null) {
            lockIpAddress(ipAddress);
        }

        boolean newVote = !voteRepository.existsByTmdbMovieIdAndAnonymousToken(tmdbMovieId, anonymousToken);
        if (newVote) {
            checkNewAnonymousVoteLimit(ipAddress, tmdbMovieId);
        }

        voteRepository.upsertAnonymousVote(tmdbMovieId, anonymousToken, hasStinger, ipAddress);

        if (newVote && attemptId != null) {
            voteAttemptRepository.markNewAnonymousVote(attemptId);
        }

        return buildSummary(tmdbMovieId, anonymousToken, null);
    }

    // Waits at most VoteAttempt.LOCK_TIMEOUT; a longer queue means a flood from one IP.
    private void lockIpAddress(String ipAddress) {
        voteAttemptRepository.limitLockWait();

        try {
            voteAttemptRepository.lockIpAddress(ipAddress);
        } catch (DataAccessException e) {
            if (SqlStates.has(e, SqlStates.LOCK_NOT_AVAILABLE)) {
                throw new RateLimitExceededException();
            }
            throw e;
        }

        voteAttemptRepository.resetLockWait();
    }

    private void releaseAttempt(Long attemptId) {
        if (attemptId == null) {
            return;
        }

        transactionTemplate.executeWithoutResult(status -> voteAttemptRepository.deleteById(attemptId));
    }

    private VoteSummaryDto buildSummary(Integer tmdbMovieId, String anonymousToken, UUID userId) {
        VoteCounts counts = countVotes(List.of(tmdbMovieId)).getOrDefault(tmdbMovieId, VoteCounts.NONE);

        Boolean userVote = null;
        Optional<Vote> own = Optional.empty();
        if (userId != null) {
            own = voteRepository.findByTmdbMovieIdAndUserId(tmdbMovieId, userId);
        } else if (anonymousToken != null) {
            own = voteRepository.findByTmdbMovieIdAndAnonymousToken(tmdbMovieId, anonymousToken);
        }
        if (own.isPresent()) {
            userVote = own.get().getHasStinger();
        }

        return new VoteSummaryDto(counts.yes(), counts.no(), counts.yes() + counts.no(), userVote,
                StingerVerdict.fromVotes(counts.yes(), counts.no()));
    }

    private Map<Integer, VoteCounts> countVotes(List<Integer> tmdbMovieIds) {
        Map<Integer, VoteCounts> counts = new HashMap<>();

        for (Object[] row : voteRepository.countVotesByMovieIds(tmdbMovieIds)) {
            counts.put((Integer) row[0], new VoteCounts(((Number) row[1]).longValue(), ((Number) row[2]).longValue()));
        }

        return counts;
    }

    private void checkNewAnonymousVoteLimit(String ipAddress, Integer tmdbMovieId) {
        if (ipAddress == null) {
            return;
        }

        long recent = voteAttemptRepository.countByIpAddressAndTmdbMovieIdAndNewAnonymousVoteTrueAndCreatedAtAfter(
                ipAddress, tmdbMovieId, Instant.now().minus(DAY));
        if (recent >= NEW_ANONYMOUS_VOTES_PER_IP_PER_MOVIE_PER_DAY) {
            throw new RateLimitExceededException();
        }
    }

    // Returns null for anonymous callers, and for sessions whose account has since been
    // deleted (other devices stay signed in until their session expires); both are then
    // handled as anonymous.
    private User resolveUser(OAuth2User oauthUser) {
        if (oauthUser == null) {
            return null;
        }

        return userRepository.findByOauthId(oauthUser.getName()).orElse(null);
    }

    private record VoteCounts(long yes, long no) {
        static final VoteCounts NONE = new VoteCounts(0, 0);
    }
}
