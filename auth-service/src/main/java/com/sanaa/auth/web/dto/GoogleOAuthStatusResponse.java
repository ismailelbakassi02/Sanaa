package com.sanaa.auth.web.dto;

/** Public feature-discovery response used to hide the button when Google is not configured. */
public record GoogleOAuthStatusResponse(boolean enabled) {
}
