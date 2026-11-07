package com.sanaa.auth.service;

/**
 * The result of attempting to rotate a refresh token. A reused token is a normal outcome of a
 * concurrent race, so it is returned instead of thrown: the caller must commit the revoke-all in a
 * new transaction before it returns 401.
 */
public record RefreshRotation(boolean reused, AuthenticatedUser user, String rawRefreshToken) {

    public static RefreshRotation reuseDetected() {
        return new RefreshRotation(true, null, null);
    }

    public static RefreshRotation success(AuthenticatedUser user, String rawRefreshToken) {
        return new RefreshRotation(false, user, rawRefreshToken);
    }
}
