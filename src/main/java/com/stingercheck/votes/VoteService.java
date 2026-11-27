package com.stingercheck.votes;

import com.stingercheck.answers.KnownAnswer;
import com.stingercheck.answers.KnownAnswerRepository;
import com.stingercheck.auth.User;
import com.stingercheck.common.BadRequestException;
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

    private static final String NEEDS_YES_VOTE = "Vote yes before adding a scene count";

    private final VoteRepository voteRepository;
    private final VoteAttemptRepository voteAttemptRepository;
    private final UserRepository userRepository;
    private final TmdbClient tmdbClient;
    private final KnownAnswerRepository knownAnswerRepository;
    private final TransactionTemplate transactionTemplate;

    public VoteService(VoteRepository voteRepository, VoteAttemptRepository voteAttemptRepository,
                       UserRepository userRepository, TmdbClient tmdbClient,
                       KnownAnswerRepository knownAnswerRepository,
                       PlatformTransactionManager transactionManager) {
        this.voteRepository = voteRepository;
        this.voteAttemptRepository = voteAttemptRepository;
        this.userRepository = userRepository;
        this.tmdbClient = tmdbClient;
        this.knownAnswerRepository = knownAnswerRepository;
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

    static final int MAX_SCENE_COUNT = 4;

    // Share of the answers the leading scene count needs; the same bar as a "yes" verdict.
    private static final double SCENE_COUNT_MAJORITY = 0.6;

    /**
     * Sets how many extra scenes the caller's own "yes" vote reports (null clears it). Only
     * edits a vote the caller already cast, so it needs no rate limit of its own.
     *
     * @throws BadRequestException when the count is out of range or the caller has no "yes" vote
     */
    @Transactional
    public VoteSummaryDto setSceneCount(Integer tmdbMovieId, Integer sceneCount,
                                        String anonymousToken, OAuth2User oauthUser) {
        if (sceneCount != null && (sceneCount < 1 || sceneCount > MAX_SCENE_COUNT)) {
            throw new BadRequestException("sceneCount must be between 1 and " + MAX_SCENE_COUNT);
        }

        User user = resolveUser(oauthUser);
        Optional<Vote> own;

        if (user != null) {
            own = voteRepository.findByTmdbMovieIdAndUserId(tmdbMovieId, user.getId());
        } else {
            AnonymousTokens.validate(anonymousToken);
            own = voteRepository.findByTmdbMovieIdAndAnonymousToken(tmdbMovieId, anonymousToken);
        }

        Vote vote = own.filter(v -> Boolean.TRUE.equals(v.getHasStinger()))
                .orElseThrow(() -> new BadRequestException(NEEDS_YES_VOTE));
        // Zero rows: the vote changed to "no" since it was read.
        if (voteRepository.setSceneCountOfYesVote(vote.getId(), sceneCount) == 0) {
            throw new BadRequestException(NEEDS_YES_VOTE);
        }

        if (user != null) {
            return buildSummary(tmdbMovieId, null, user.getId());
        }
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

    /** Verdict per movie id: the known answer when there is one, else from the votes; others are UNKNOWN. */
    @Transactional(readOnly = true)
    public Map<Integer, StingerVerdict> verdictsFor(List<Integer> tmdbMovieIds) {
        Map<Integer, StingerVerdict> verdicts = new HashMap<>();

        if (tmdbMovieIds.isEmpty()) {
            return verdicts;
        }

        countVotes(tmdbMovieIds).forEach((id, counts) ->
                verdicts.put(id, StingerVerdict.fromVotes(counts.yes(), counts.no())));
        knownAnswerRepository.findFor(tmdbMovieIds).forEach((id, known) -> verdicts.put(id, verdictOf(known)));
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
        Integer userSceneCount = null;
        Optional<Vote> own = Optional.empty();
        if (userId != null) {
            own = voteRepository.findByTmdbMovieIdAndUserId(tmdbMovieId, userId);
        } else if (anonymousToken != null) {
            own = voteRepository.findByTmdbMovieIdAndAnonymousToken(tmdbMovieId, anonymousToken);
        }
        if (own.isPresent()) {
            userVote = own.get().getHasStinger();
            userSceneCount = own.get().getSceneCount();
        }

        // A known answer wins over the votes. Its own scene count wins too; without one, the voters' count is used.
        KnownAnswer known = knownAnswerRepository.findFor(List.of(tmdbMovieId)).get(tmdbMovieId);
        StingerVerdict verdict;
        Integer sceneCount;
        if (known == null) {
            verdict = StingerVerdict.fromVotes(counts.yes(), counts.no());
            sceneCount = leadingSceneCount(tmdbMovieId);
        } else if (!known.hasStinger()) {
            verdict = StingerVerdict.NO;
            sceneCount = null;
        } else {
            verdict = StingerVerdict.YES;
            sceneCount = known.sceneCount() != null ? known.sceneCount() : leadingSceneCount(tmdbMovieId);
        }

        return new VoteSummaryDto(counts.yes(), counts.no(), counts.yes() + counts.no(), userVote,
                verdict, sceneCount, userSceneCount, known != null);
    }

    private static StingerVerdict verdictOf(KnownAnswer known) {
        return known.hasStinger() ? StingerVerdict.YES : StingerVerdict.NO;
    }

    // The count most "yes" voters gave, once as many answered as a verdict needs votes and a
    // clear majority agree; null before that, so one voter can't put a number on the page.
    private Integer leadingSceneCount(Integer tmdbMovieId) {
        Integer leading = null;
        long leadingVotes = 0;
        long answers = 0;

        for (Object[] row : voteRepository.countSceneCountsByMovieId(tmdbMovieId)) {
            int count = ((Number) row[0]).intValue();
            long votes = ((Number) row[1]).longValue();
            answers += votes;

            if (votes > leadingVotes) {
                leading = count;
                leadingVotes = votes;
            }
        }

        // A tie can't reach the majority, so tied answers give no count either.
        if (answers < StingerVerdict.MIN_VOTES || (double) leadingVotes / answers < SCENE_COUNT_MAJORITY) {
            return null;
        }
        return leading;
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
