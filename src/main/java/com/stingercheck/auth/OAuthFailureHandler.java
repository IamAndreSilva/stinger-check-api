package com.stingercheck.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/**
 * Sends the user back to the page the sign-in started from when it fails or is cancelled at
 * Google. Spring's default goes to /login?error, which is a 404 here because the site has no
 * login page.
 *
 * <p>Unlike the default, it doesn't store the error in a new session. Anyone can call the
 * callback URL, and each such session would take about 9 KB of the capped session Redis.
 */
@Component
public class OAuthFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuthFailureHandler.class);

    // Expected outcomes rather than faults: the user cancelled at Google, or the callback came
    // without a sign-in in progress (an expired session, a reload, or a forged request).
    private static final Set<String> EXPECTED_ERROR_CODES = Set.of("access_denied", "authorization_request_not_found");

    private static final int MAX_LOGGED_LENGTH = 100;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        if (isExpected(exception)) {
            log.debug("Sign-in not completed: {}", describe(exception));
        } else {
            log.warn("Sign-in failed: {}", describe(exception));
        }

        response.sendRedirect(frontendUrl + ReturnToAuthorizationRequestResolver.takeReturnPath(request));
    }

    // The error code comes from the callback URL, so anyone can choose it: keep it short and on
    // one line, so it can't forge log lines.
    private static String describe(AuthenticationException exception) {
        String reason = exception instanceof OAuth2AuthenticationException oauthException
                ? oauthException.getError().getErrorCode()
                : exception.getClass().getSimpleName();
        String printable = String.valueOf(reason).replaceAll("\\p{Cntrl}", "?");
        return printable.length() > MAX_LOGGED_LENGTH ? printable.substring(0, MAX_LOGGED_LENGTH) + "…" : printable;
    }

    private static boolean isExpected(AuthenticationException exception) {
        return exception instanceof OAuth2AuthenticationException oauthException
                && EXPECTED_ERROR_CODES.contains(oauthException.getError().getErrorCode());
    }
}
