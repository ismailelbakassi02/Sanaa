package com.sanaa.auth.web;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sanaa.auth.config.GoogleOAuthProperties;
import com.sanaa.auth.error.NotFoundException;
import com.sanaa.auth.service.AuthFacade;
import com.sanaa.auth.service.AuthResult;
import com.sanaa.auth.service.GoogleLinkCookieService;
import com.sanaa.auth.service.RefreshCookieService;
import com.sanaa.auth.web.dto.GoogleLinkRequest;
import com.sanaa.auth.web.dto.GoogleOAuthStatusResponse;
import com.sanaa.auth.web.dto.LoginResponse;

/** Public Google login discovery, authorization start and secure existing-account linking. */
@RestController
@RequestMapping("/api/auth/oauth2")
public class GoogleOAuthController {

    private final GoogleOAuthProperties properties;
    private final AuthFacade authFacade;
    private final RefreshCookieService refreshCookieService;
    private final GoogleLinkCookieService linkCookieService;

    public GoogleOAuthController(GoogleOAuthProperties properties, AuthFacade authFacade,
                                 RefreshCookieService refreshCookieService,
                                 GoogleLinkCookieService linkCookieService) {
        this.properties = properties;
        this.authFacade = authFacade;
        this.refreshCookieService = refreshCookieService;
        this.linkCookieService = linkCookieService;
    }

    @GetMapping("/google/status")
    public GoogleOAuthStatusResponse status() {
        return new GoogleOAuthStatusResponse(properties.enabled());
    }

    @GetMapping("/google")
    public ResponseEntity<Void> start() {
        requireEnabled();
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/api/auth/oauth2/authorization/google"))
                .build();
    }

    @PostMapping("/google/link")
    public ResponseEntity<LoginResponse> link(
            @CookieValue(name = GoogleLinkCookieService.COOKIE_NAME, required = false)
            String rawTicket,
            @Valid @RequestBody GoogleLinkRequest request) {
        requireEnabled();
        AuthResult result = authFacade.confirmGoogleLink(rawTicket, request.password());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookieService.issue(result.refreshToken()))
                .header(HttpHeaders.SET_COOKIE, linkCookieService.clear())
                .body(result.response());
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new NotFoundException("Google login is not available.");
        }
    }
}
