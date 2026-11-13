package com.stingercheck.auth;

import com.stingercheck.common.ConcurrentChangeException;
import com.stingercheck.common.SqlStates;
import com.stingercheck.votes.AnonymousTokens;
import com.stingercheck.votes.VoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    // One retry is enough: the unique violation only happens once the competing vote is
    // committed, and a fresh transaction then sees it and skips that movie.
    private static final int CLAIM_ATTEMPTS = 2;

    private final UserRepository userRepository;
    private final VoteRepository voteRepository;
    private final TransactionTemplate transactionTemplate;

    public AccountService(UserRepository userRepository, VoteRepository voteRepository,
                          PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.voteRepository = voteRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Permanently removes the user. Votes cast while signed in are kept in the tallies but
     * anonymized, so they can no longer be linked to the person.
     */
    @Transactional
    public void deleteAccount(String oauthId) {
        userRepository.findByOauthId(oauthId).ifPresent(user -> {
            voteRepository.anonymizeByUserId(user.getId());
            userRepository.delete(user);
        });
    }

    /**
     * Moves the votes this browser cast anonymously to the signed-in user, so signing in
     * doesn't let someone vote twice on the same movie. Where the user already voted on a
     * movie, their signed-in vote wins and the anonymous one is removed.
     *
     * @return how many anonymous votes were moved to the user
     * @throws ConcurrentChangeException when a signed-in vote kept racing the claim
     */
    public int claimAnonymousVotes(String oauthId, String anonymousToken) {
        AnonymousTokens.validate(anonymousToken);

        for (int attempt = 1; ; attempt++) {
            try {
                Integer claimed = transactionTemplate.execute(status -> claimOnce(oauthId, anonymousToken));
                return claimed == null ? 0 : claimed;
            } catch (DataIntegrityViolationException e) {
                // Only the race with a signed-in vote on the same movie is expected here.
                if (!SqlStates.has(e, SqlStates.UNIQUE_VIOLATION)) {
                    throw e;
                }
                if (attempt >= CLAIM_ATTEMPTS) {
                    throw new ConcurrentChangeException(e);
                }
                log.debug("Claiming anonymous votes raced a signed-in vote; retrying");
            }
        }
    }

    private int claimOnce(String oauthId, String anonymousToken) {
        return userRepository.findByOauthId(oauthId)
                .map(user -> {
                    int claimed = voteRepository.claimAnonymousVotes(anonymousToken, user.getId());
                    voteRepository.deleteAnonymousVotes(anonymousToken);
                    return claimed;
                })
                .orElse(0);
    }
}
