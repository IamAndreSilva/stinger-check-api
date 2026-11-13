package com.stingercheck.auth;

import com.stingercheck.votes.AnonymousTokens;
import com.stingercheck.votes.VoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final UserRepository userRepository;
    private final VoteRepository voteRepository;

    public AccountService(UserRepository userRepository, VoteRepository voteRepository) {
        this.userRepository = userRepository;
        this.voteRepository = voteRepository;
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
     */
    @Transactional
    public int claimAnonymousVotes(String oauthId, String anonymousToken) {
        AnonymousTokens.validate(anonymousToken);

        return userRepository.findByOauthId(oauthId)
                .map(user -> {
                    int claimed = voteRepository.claimAnonymousVotes(anonymousToken, user.getId());
                    voteRepository.deleteAnonymousVotes(anonymousToken);
                    return claimed;
                })
                .orElse(0);
    }
}
