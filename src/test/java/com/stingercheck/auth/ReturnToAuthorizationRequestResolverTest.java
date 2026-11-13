package com.stingercheck.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;

class ReturnToAuthorizationRequestResolverTest {

    private static final String BASE_URI = "/api/oauth2/authorization";

    private final ReturnToAuthorizationRequestResolver resolver = new ReturnToAuthorizationRequestResolver(
            new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                    .clientId("test-client").clientSecret("test-secret").build()),
            BASE_URI);

    @Test
    void acceptsPathsOnThisSiteOnly() {
        assertThat(ReturnToAuthorizationRequestResolver.isSafePath("/movies/1726-iron-man")).isTrue();
        assertThat(ReturnToAuthorizationRequestResolver.isSafePath("/?q=iron")).isTrue();

        assertThat(ReturnToAuthorizationRequestResolver.isSafePath(null)).isFalse();
        assertThat(ReturnToAuthorizationRequestResolver.isSafePath("https://evil.example")).isFalse();
        assertThat(ReturnToAuthorizationRequestResolver.isSafePath("//evil.example")).isFalse();
        assertThat(ReturnToAuthorizationRequestResolver.isSafePath("/\\evil.example")).isFalse();
        assertThat(ReturnToAuthorizationRequestResolver.isSafePath("/movies\r\nSet-Cookie: x")).isFalse();
    }

    @Test
    void remembersTheReturnPathOfASignInStart() {
        MockHttpServletRequest request = signInRequest("/movies/1726-iron-man");

        assertThat(resolver.resolve(request)).isNotNull();
        assertThat(ReturnToAuthorizationRequestResolver.takeReturnPath(request)).isEqualTo("/movies/1726-iron-man");
        // Taken once: a later sign-in without returnTo goes to the home page.
        assertThat(ReturnToAuthorizationRequestResolver.takeReturnPath(request)).isEqualTo("/");
    }

    @Test
    void ignoresUnsafeReturnPaths() {
        MockHttpServletRequest request = signInRequest("//evil.example");

        resolver.resolve(request);

        assertThat(ReturnToAuthorizationRequestResolver.takeReturnPath(request)).isEqualTo("/");
    }

    private static MockHttpServletRequest signInRequest(String returnTo) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", BASE_URI + "/google");
        request.setServletPath(BASE_URI + "/google");
        request.setParameter("returnTo", returnTo);
        return request;
    }
}
