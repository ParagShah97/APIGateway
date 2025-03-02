package com.parag.CloudGateway.controller;

import com.parag.CloudGateway.models.AuthenticationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/authenticate")
public class AuthenticationController {

    @GetMapping("/login")
    public ResponseEntity<AuthenticationResponse> login(
            @AuthenticationPrincipal OidcUser oidcUser,
            Model model,
            @RegisteredOAuth2AuthorizedClient("okta")
            OAuth2AuthorizedClient client) {
//        String refreshToken = client.getRefreshToken() != null ? client.getRefreshToken().getTokenValue() : null;
        AuthenticationResponse authenticationResponse =
            new AuthenticationResponse(oidcUser.getEmail(),
                    client.getAccessToken().getTokenValue(),
//                    client.getRefreshToken().getTokenValue(),
                    "This is a refresh token",
                    client.getAccessToken().getExpiresAt().getEpochSecond(),
                    oidcUser.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.toList()));
    return new ResponseEntity<>(authenticationResponse, HttpStatus.OK);
    }
}
