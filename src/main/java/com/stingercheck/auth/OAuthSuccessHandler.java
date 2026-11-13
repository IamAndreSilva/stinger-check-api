package com.stingercheck.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuthSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public OAuthSuccessHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();

        String oauthId = oauthUser.getName();
        String email   = oauthUser.getAttribute("email");
        String name    = oauthUser.getAttribute("name");
        String avatar  = oauthUser.getAttribute("picture");

        userRepository.findByOauthId(oauthId).ifPresentOrElse(
            existing -> {
                existing.setName(name);
                existing.setAvatarUrl(avatar);
                userRepository.save(existing);
            },
            () -> userRepository.save(new User(email, name, avatar, oauthId))
        );

        getRedirectStrategy().sendRedirect(request, response, frontendUrl);
    }
}
