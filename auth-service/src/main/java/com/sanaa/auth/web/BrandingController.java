package com.sanaa.auth.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sanaa.auth.service.AuthFacade;
import com.sanaa.auth.web.dto.BrandingResponse;

@RestController
@RequestMapping("/api/auth/tenants")
public class BrandingController {

    private final AuthFacade authFacade;

    public BrandingController(AuthFacade authFacade) {
        this.authFacade = authFacade;
    }

    @GetMapping("/{slug}/branding")
    public BrandingResponse branding(@PathVariable String slug) {
        return authFacade.branding(slug);
    }
}
