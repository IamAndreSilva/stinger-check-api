package com.stingercheck.votes;

import com.stingercheck.auth.User;
import com.stingercheck.auth.UserRepository;
import com.stingercheck.movies.TmdbClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public VoteService(VoteRepository voteRepository, VoteAttemptRepository voteAttemptRepository,
                       UserRepository userRepository, TmdbClient tmdbClient) {
        this.voteRepository = voteRepository;
        this.voteAttemptRepository = voteAttemptRepository;
        this.userRepository = userRepository;
        this.tmdbClient = tmdbClient;
    }

    /** @throws com.stingercheck.movies.MovieNotFoundException when TMDB doesn't know the movie */
    @Transactional
    public VoteSummaryDto castVote(Integer tmdbMovieId, boolean hasStinger,
                                   String anonymousToken, OAuth2User oauthUser, String ipAddress) {
        User user = resolveUser(oauthUser);

        if (user == null) {
            AnonymousTokens.validate(anonymousToken);
        }

        checkHourlyLimit(ipAddress);
        // Only real movies can be voted on; the lookup is cached.
        tmdbClient.getMovie(tmdbMovieId);

        if (user != null) {
            voteRepository.upsertUserVote(tmdbMovieId, user.getId(), hasStinger, ipAddress);
            recordAttempt(ipAddress, tmdbMovieId, false);
            return buildSummary(tmdbMovieId, null, user.getId());
        }

        boolean newVote = !voteRepository.existsByTmdbMovieIdAndAnonymousToken(tmdbMovieId, anonymousToken);
        if (newVote) {
            checkNewAnonymousVoteLimit(ipAddress, tmdbMovieId);
        }

        voteRepository.upsertAnonymousVote(tmdbMovieId, anonymousToken, hasStinger, ipAddress);
        recordAttempt(ipAddress, tmdbMovieId, newVote);
        return buildSummary(tmdbMovieId, anonymousToken, null);
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

    private void checkHourlyLimit(String ipAddress) {
        if (ipAddress == null) {
            return;
        }

        long recent = voteAttemptRepository.countByIpAddressAndCreatedAtAfter(ipAddress, Instant.now().minus(HOUR));
        if (recent >= VOTES_PER_IP_PER_HOUR) {
            throw new RateLimitExceededException();
        }
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

    private void recordAttempt(String ipAddress, Integer tmdbMovieId, boolean newAnonymousVote) {
        if (ipAddress == null) {
            return;
        }

        voteAttemptRepository.save(new VoteAttempt(ipAddress, tmdbMovieId, newAnonymousVote));
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
