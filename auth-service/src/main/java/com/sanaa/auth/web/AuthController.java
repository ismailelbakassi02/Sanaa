package com.sanaa.auth.web;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sanaa.auth.service.AuthFacade;
import com.sanaa.auth.service.AuthResult;
import com.sanaa.auth.service.RefreshCookieService;
import com.sanaa.auth.web.dto.ChangePasswordRequest;
import com.sanaa.auth.web.dto.LoginRequest;
import com.sanaa.auth.web.dto.LoginResponse;
import com.sanaa.auth.web.dto.MeResponse;
import com.sanaa.auth.web.dto.RegisterRequest;
import com.sanaa.auth.web.dto.RegisterResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthFacade authFacade;
    private final RefreshCookieService refreshCookieService;

    public AuthController(AuthFacade authFacade, RefreshCookieService refreshCookieService) {
        this.authFacade = authFacade;
        this.refreshCookieService = refreshCookieService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authFacade.register(request);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResult result = authFacade.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookieService.issue(result.refreshToken()))
                .body(result.response());
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = RefreshCookieService.COOKIE_NAME, required = false)
            String refreshToken) {
        AuthResult result = authFacade.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookieService.issue(result.refreshToken()))
                .body(result.response());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookieService.COOKIE_NAME, required = false)
            String refreshToken) {
        authFacade.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookieService.clear())
                .build();
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authFacade.me(jwt);
    }

    /**
     * Available even when {@code must_change_password} is true. A successful change returns a new
     * access token and refresh cookie, so the caller leaves the restricted state immediately.
     */
    @PostMapping("/password")
    public ResponseEntity<LoginResponse> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request) {
        AuthResult result = authFacade.changePassword(AuthFacade.userId(jwt), request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookieService.issue(result.refreshToken()))
                .body(result.response());
    }
}
