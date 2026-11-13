package com.stingercheck;

import com.stingercheck.auth.User;
import com.stingercheck.auth.UserRepository;
import com.stingercheck.movies.TmdbClient;
import com.stingercheck.movies.TmdbMovieResult;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountDeletionEndpointTests {

    private static final String ACCOUNT_URL = "/api/auth/me";
    private static final String VOTE_URL = "/api/movies/999002/votes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private TmdbClient tmdbClient;

    // Votes are only accepted for movies TMDB knows.
    @BeforeEach
    void movieExists() {
        when(tmdbClient.getMovie(anyInt())).thenReturn(new TmdbMovieResult(999_002, "Test Movie", "2024-01-01", null, null, null, null));
    }

    @Test
    void anonymousCallerIsRejected() throws Exception {
        mockMvc.perform(delete(ACCOUNT_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signedInUserCanDeleteOwnAccount() throws Exception {
        String oauthId = "test-" + UUID.randomUUID();
        userRepository.saveAndFlush(new User(oauthId + "@example.test", "Test User", null, oauthId));

        mockMvc.perform(delete(ACCOUNT_URL)
                        .with(oauth2Login().attributes(attributes -> attributes.put("sub", oauthId))))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findByOauthId(oauthId)).isEmpty();
    }

    @Test
    void sessionOfDeletedAccountIsTreatedAsAnonymous() throws Exception {
        String oauthIdWithoutAccount = "test-" + UUID.randomUUID();

        mockMvc.perform(post(VOTE_URL)
                        .with(oauth2Login().attributes(attributes -> attributes.put("sub", oauthIdWithoutAccount)))
                        .header("X-Anonymous-Token", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hasStinger\":true}"))
                .andExpect(status().isOk());
    }
}
