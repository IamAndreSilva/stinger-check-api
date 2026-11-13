package com.stingercheck;

import com.stingercheck.auth.User;
import com.stingercheck.auth.UserRepository;
import com.stingercheck.votes.Vote;
import com.stingercheck.votes.VoteRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Signing in moves the browser's anonymous votes to the account, so nobody votes twice. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClaimVotesTests {

    private static final String CLAIM_URL = "/api/auth/me/claim-votes";
    private static final int ONLY_ANONYMOUS_MOVIE = 999_501;
    private static final int BOTH_MOVIE = 999_502;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void movesAnonymousVotesAndKeepsTheSignedInVoteOnConflict() throws Exception {
        String oauthId = "test-" + UUID.randomUUID();
        User user = userRepository.saveAndFlush(new User(oauthId + "@example.test", "Test User", null, oauthId));
        String token = UUID.randomUUID().toString();
        voteRepository.saveAndFlush(new Vote(ONLY_ANONYMOUS_MOVIE, true, token, null, null));
        voteRepository.saveAndFlush(new Vote(BOTH_MOVIE, true, token, null, null));
        voteRepository.saveAndFlush(new Vote(BOTH_MOVIE, false, null, user, null));

        mockMvc.perform(post(CLAIM_URL)
                        .with(oauth2Login().attributes(attributes -> attributes.put("sub", oauthId)))
                        .header("X-Anonymous-Token", token))
                .andExpect(status().isNoContent());
        entityManager.clear();

        Vote claimed = voteRepository.findByTmdbMovieIdAndUserId(ONLY_ANONYMOUS_MOVIE, user.getId()).orElseThrow();
        assertThat(claimed.getHasStinger()).isTrue();
        assertThat(claimed.getAnonymousToken()).isNull();

        assertThat(voteRepository.findByTmdbMovieIdAndUserId(BOTH_MOVIE, user.getId()).orElseThrow().getHasStinger()).isFalse();
        assertThat(voteRepository.findByTmdbMovieIdAndAnonymousToken(BOTH_MOVIE, token)).isEmpty();
        assertThat(voteRepository.findByTmdbMovieIdAndAnonymousToken(ONLY_ANONYMOUS_MOVIE, token)).isEmpty();
    }

    @Test
    void anonymousCallerIsRejected() throws Exception {
        mockMvc.perform(post(CLAIM_URL).header("X-Anonymous-Token", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }
}
