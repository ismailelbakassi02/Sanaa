package com.sanaa.auth.web;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sanaa.auth.service.AuthFacade;
import com.sanaa.auth.service.ProfileService;
import com.sanaa.auth.web.dto.ProfileResponse;
import com.sanaa.auth.web.dto.UpdateProfileRequest;

@RestController
@RequestMapping("/api/auth/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
        return profileService.get(AuthFacade.userId(jwt));
    }

    @PutMapping
    public ProfileResponse update(@AuthenticationPrincipal Jwt jwt,
                                  @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.update(AuthFacade.userId(jwt), request);
    }
}
