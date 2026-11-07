package com.sanaa.auth.service;

import com.sanaa.auth.web.dto.LoginResponse;

/**
 * The access token response plus the raw refresh token that must go into the HttpOnly cookie. The
 * refresh token is deliberately not part of the JSON body.
 */
public record AuthResult(LoginResponse response, String refreshToken) {
}
