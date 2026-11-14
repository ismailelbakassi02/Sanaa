package com.sanaa.auth.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sanaa.auth.service.ImageService;
import com.sanaa.auth.web.dto.ImageUploadResponse;

@RestController
@RequestMapping("/api/auth/images")
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ImageUploadResponse upload(@RequestParam String kind,
                                      @RequestParam("file") MultipartFile file,
                                      @AuthenticationPrincipal Jwt jwt) {
        return imageService.upload(kind, file, jwt);
    }
}
