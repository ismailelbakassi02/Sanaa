package com.sanaa.auth.web;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sanaa.auth.service.AuthFacade;
import com.sanaa.auth.web.dto.LoginRequest;
import com.sanaa.auth.web.dto.LoginResponse;
import com.sanaa.auth.web.dto.MeResponse;
import com.sanaa.auth.web.dto.RegisterRequest;
import com.sanaa.auth.web.dto.RegisterResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthFacade authFacade;

    public AuthController(AuthFacade authFacade) {
        this.authFacade = authFacade;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authFacade.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authFacade.login(request);
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authFacade.me(jwt);
    }
}
