package com.stingercheck.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final AccountService accountService;

    public AuthController(UserRepository userRepository, AccountService accountService) {
        this.userRepository = userRepository;
        this.accountService = accountService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal OAuth2User oauthUser) {
        // 204 rather than an empty 200: nobody is signed in (or the account was deleted).
        if (oauthUser == null) {
            return ResponseEntity.noContent().build();
        }

        return userRepository.findByOauthId(oauthUser.getName())
                .map(UserDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal OAuth2User oauthUser,
                                              HttpServletRequest request) throws Exception {
        if (oauthUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        accountService.deleteAccount(oauthUser.getName());
        request.logout();

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/claim-votes")
    public ResponseEntity<Void> claimAnonymousVotes(
            @AuthenticationPrincipal OAuth2User oauthUser,
            @RequestHeader(value = "X-Anonymous-Token", required = false) String anonymousToken) {
        if (oauthUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        accountService.claimAnonymousVotes(oauthUser.getName(), anonymousToken);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) throws Exception {
        request.logout();
        return ResponseEntity.noContent().build();
    }
}
