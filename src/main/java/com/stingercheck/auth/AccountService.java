package com.stingercheck.auth;

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
}
