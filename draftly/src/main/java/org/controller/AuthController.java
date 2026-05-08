package org.controller;

import org.entity.User;
import org.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private OAuth2AuthorizedClientService clientService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/success")
    public String success(OAuth2AuthenticationToken auth) {

        OAuth2AuthorizedClient client =
                clientService.loadAuthorizedClient(
                        auth.getAuthorizedClientRegistrationId(),
                        auth.getName()
                );

        String accessToken = client.getAccessToken().getTokenValue();
        String email = auth.getPrincipal().getAttribute("email");
        String refreshToken = (client.getRefreshToken() != null) ? client.getRefreshToken().getTokenValue() : null;

        // Save user
        User user = userRepository.findByEmail(email)
                .orElse(new User());

        user.setEmail(email);
        user.setAccessToken(accessToken);
        user.setRefreshToken(refreshToken);

        userRepository.save(user);

        return "Login successful for " + email + (refreshToken == null ? " (no refresh token issued)" : " (refresh token saved)");
    }
}
