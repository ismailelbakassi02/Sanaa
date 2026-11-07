package com.sanaa.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.auth.domain.User;
import com.sanaa.auth.domain.UserStatus;
import com.sanaa.auth.error.UnauthenticatedException;
import com.sanaa.auth.repository.UserRepository;
import com.sanaa.auth.web.dto.MeResponse;
import com.sanaa.common.security.JwtClaims;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Reads the current user under the tenant context that {@code JwtTenantFilter} set before the
 * controller ran. The subject is the numeric user id; {@code @TenantId} makes a foreign id
 * invisible even if the token were tampered with.
 */
@Service
public class UserQueryService {

    private final UserRepository userRepository;

    public UserQueryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public MeResponse currentUser(Jwt jwt) {
        long userId;
        try {
            userId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException | NullPointerException exception) {
            throw new UnauthenticatedException();
        }

        User user = userRepository.findById(userId).orElseThrow(UnauthenticatedException::new);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthenticatedException();
        }

        return new MeResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getRole().name(), user.getTenantId(),
                jwt.getClaimAsString(JwtClaims.TENANT_SLUG));
    }
}
