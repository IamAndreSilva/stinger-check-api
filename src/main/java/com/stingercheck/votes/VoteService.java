package com.stingercheck.votes;

import com.stingercheck.auth.User;
import com.stingercheck.auth.UserRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class VoteService {

    private static final int IP_RATE_LIMIT_PER_HOUR = 20;

    private final VoteRepository voteRepository;
    private final UserRepository userRepository;

    public VoteService(VoteRepository voteRepository, UserRepository userRepository) {
        this.voteRepository = voteRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public VoteSummaryDto castVote(Integer tmdbMovieId, boolean hasStinger,
                                   String anonymousToken, OAuth2User oauthUser, String ipAddress) {
        checkRateLimit(ipAddress);

        User user = resolveUser(oauthUser);

        if (user != null) {
            voteRepository.findByTmdbMovieIdAndUserId(tmdbMovieId, user.getId())
                    .ifPresentOrElse(
                            v -> v.setHasStinger(hasStinger),
                            () -> voteRepository.save(new Vote(tmdbMovieId, hasStinger, null, user, ipAddress))
                    );
            return buildSummary(tmdbMovieId, null, user.getId());
        }

        validateAnonymousToken(anonymousToken);
        voteRepository.findByTmdbMovieIdAndAnonymousToken(tmdbMovieId, anonymousToken)
                .ifPresentOrElse(
                        v -> v.setHasStinger(hasStinger),
                        () -> voteRepository.save(new Vote(tmdbMovieId, hasStinger, anonymousToken, null, ipAddress))
                );
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

        validateAnonymousToken(anonymousToken);
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

    private VoteSummaryDto buildSummary(Integer tmdbMovieId, String anonymousToken, UUID userId) {
        long yes = voteRepository.countYesByMovieId(tmdbMovieId);
        long no  = voteRepository.countNoByMovieId(tmdbMovieId);

        Boolean userVote = null;
        if (userId != null) {
            Optional<Vote> v = voteRepository.findByTmdbMovieIdAndUserId(tmdbMovieId, userId);
            if (v.isPresent()) userVote = v.get().getHasStinger();
        } else if (anonymousToken != null) {
            Optional<Vote> v = voteRepository.findByTmdbMovieIdAndAnonymousToken(tmdbMovieId, anonymousToken);
            if (v.isPresent()) userVote = v.get().getHasStinger();
        }

        return new VoteSummaryDto(yes, no, yes + no, userVote);
    }

    private void checkRateLimit(String ipAddress) {
        if (ipAddress == null) return;
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        long count = voteRepository.countByIpAddressSince(ipAddress, oneHourAgo);
        if (count >= IP_RATE_LIMIT_PER_HOUR) {
            throw new RateLimitExceededException();
        }
    }

    private void validateAnonymousToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Anonymous token required");
        }
        try {
            UUID.fromString(token);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid anonymous token format");
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
}
