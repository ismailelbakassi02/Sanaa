package com.sanaa.auth.web;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sanaa.auth.error.ForbiddenException;
import com.sanaa.auth.service.PublisherService;
import com.sanaa.auth.web.dto.CreatePublisherRequest;
import com.sanaa.auth.web.dto.CreatedPublisherResponse;
import com.sanaa.auth.web.dto.PublisherProfileResponse;
import com.sanaa.auth.web.dto.SlugAvailabilityResponse;
import com.sanaa.auth.web.dto.UpdatePublisherRequest;
import com.sanaa.common.security.JwtClaims;

@RestController
@RequestMapping("/api/auth/publishers")
public class PublisherController {

    private final PublisherService publisherService;

    public PublisherController(PublisherService publisherService) {
        this.publisherService = publisherService;
    }

    /**
     * Public by design: an anonymous visitor can create a publisher with owner data, and a logged-in
     * learner can become a creator without creating a second account. The presence of a JWT is the
     * only difference; the body must match the case exactly.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedPublisherResponse create(@Valid @RequestBody CreatePublisherRequest request,
                                           @AuthenticationPrincipal Jwt jwt) {
        return publisherService.create(request, jwt);
    }

    @GetMapping("/slug-available")
    public SlugAvailabilityResponse slugAvailable(@RequestParam String slug) {
        return publisherService.slugAvailable(slug);
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public PublisherProfileResponse updateOwn(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody UpdatePublisherRequest request) {
        String tenantId = jwt.getClaimAsString(JwtClaims.TENANT_ID);
        if (tenantId == null) {
            throw new ForbiddenException("A publisher admin session is required.");
        }
        return publisherService.updatePublisher(tenantId, request);
    }
}
