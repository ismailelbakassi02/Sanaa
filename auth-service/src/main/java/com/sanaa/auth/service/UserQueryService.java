package com.sanaa.auth.service;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.auth.domain.User;
import com.sanaa.auth.domain.UserStatus;
import com.sanaa.auth.error.UnauthenticatedException;
import com.sanaa.auth.repository.UserRepository;
import com.sanaa.auth.web.dto.MeResponse;
import com.sanaa.common.security.JwtClaims;

/**
 * Answers {@code /api/auth/me} from the validated token.
 *
 * <p>Role and publisher claims come from the token (they are its whole purpose); the account status
 * and the password-change flag are re-read from the database so a user disabled or forced to change
 * password after the token was issued is reflected immediately.
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

        String role = jwt.getClaimAsString(JwtClaims.ROLE);
        return new MeResponse(user.getId(), user.getEmail(), user.getFullName(),
                role == null ? "STUDENT" : role,
                jwt.getClaimAsString(JwtClaims.TENANT_ID),
                jwt.getClaimAsString(JwtClaims.TENANT_SLUG),
                jwt.getClaimAsString(JwtClaims.TENANT_TYPE),
                user.isMustChangePassword());
    }
}
