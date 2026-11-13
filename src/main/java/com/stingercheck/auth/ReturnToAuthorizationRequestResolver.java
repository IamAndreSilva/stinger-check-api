package com.stingercheck.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.regex.Pattern;

/**
 * Remembers the page a sign-in started from ({@code ?returnTo=/movies/1726-iron-man} on the
 * authorization URL), so {@link OAuthSuccessHandler} can send the user back there.
 */
public class ReturnToAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    static final String RETURN_TO_PARAMETER = "returnTo";
    static final String DEFAULT_RETURN_PATH = "/";

    private static final String SESSION_ATTRIBUTE = ReturnToAuthorizationRequestResolver.class.getName() + ".RETURN_TO";
    private static final int MAX_RETURN_PATH_LENGTH = 512;

    // A path on this site only: one leading slash, not followed by another slash or a
    // backslash (browsers read //evil.com and /\evil.com as other hosts), and no whitespace.
    private static final Pattern SAFE_PATH = Pattern.compile("^/(?![/\\\\])[^\\s\\\\]*$");

    private final OAuth2AuthorizationRequestResolver delegate;

    public ReturnToAuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository,
                                                String authorizationBaseUri) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, authorizationBaseUri);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return remember(request, delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return remember(request, delegate.resolve(request, clientRegistrationId));
    }

    /** Removes and returns the remembered path, or "/" when there is none. */
    static String takeReturnPath(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session == null) {
            return DEFAULT_RETURN_PATH;
        }

        Object path = session.getAttribute(SESSION_ATTRIBUTE);
        session.removeAttribute(SESSION_ATTRIBUTE);

        if (path instanceof String returnPath && isSafePath(returnPath)) {
            return returnPath;
        }

        return DEFAULT_RETURN_PATH;
    }

    static boolean isSafePath(String path) {
        return path != null && path.length() <= MAX_RETURN_PATH_LENGTH && SAFE_PATH.matcher(path).matches();
    }

    // The delegate returns null for requests that aren't a sign-in start; leave those alone.
    private OAuth2AuthorizationRequest remember(HttpServletRequest request, OAuth2AuthorizationRequest authorizationRequest) {
        if (authorizationRequest == null) {
            return null;
        }

        String returnTo = request.getParameter(RETURN_TO_PARAMETER);
        HttpSession session = request.getSession();

        if (isSafePath(returnTo)) {
            session.setAttribute(SESSION_ATTRIBUTE, returnTo);
        } else {
            session.removeAttribute(SESSION_ATTRIBUTE);
        }

        return authorizationRequest;
    }
}
