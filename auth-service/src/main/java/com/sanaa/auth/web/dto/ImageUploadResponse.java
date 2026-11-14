package com.sanaa.auth.web.dto;

public record ImageUploadResponse(
        String id,
        String kind,
        String contentType,
        long sizeBytes) {
}
