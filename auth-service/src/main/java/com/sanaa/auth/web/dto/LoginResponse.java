package com.sanaa.auth.web.dto;

public record LoginResponse(String accessToken, long expiresIn) {
}
