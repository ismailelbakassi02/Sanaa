package com.sanaa.auth.web;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sanaa.auth.service.ImageService;
import com.sanaa.auth.service.PublicService;
import com.sanaa.auth.web.dto.PublicInstructorResponse;
import com.sanaa.auth.web.dto.PublicPublisherResponse;

/**
 * Anonymous pages. They are the public cross-tenant read model: no token, no email, only active
 * publishers and active memberships.
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final PublicService publicService;
    private final ImageService imageService;

    public PublicController(PublicService publicService, ImageService imageService) {
        this.publicService = publicService;
        this.imageService = imageService;
    }

    @GetMapping("/publishers/{slug}")
    public PublicPublisherResponse publisher(@PathVariable String slug) {
        return publicService.publisher(slug);
    }

    @GetMapping("/instructors/{userId}")
    public PublicInstructorResponse instructor(@PathVariable long userId) {
        return publicService.instructor(userId);
    }

    @GetMapping("/images/{id}")
    public ResponseEntity<byte[]> image(@PathVariable String id) {
        ImageService.StoredImage image = imageService.load(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.bytes());
    }
}
