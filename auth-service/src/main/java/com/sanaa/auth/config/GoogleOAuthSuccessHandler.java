package com.sanaa.auth.config;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.sanaa.auth.service.AuthFacade;
import com.sanaa.auth.service.AuthResult;
import com.sanaa.auth.service.GoogleLinkCookieService;
import com.sanaa.auth.service.GoogleLoginOutcome;
import com.sanaa.auth.service.GoogleProfile;
import com.sanaa.auth.service.RefreshCookieService;

/** Converts Google's verified principal into the existing SANAA refresh session. */
@Component
public class GoogleOAuthSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthSuccessHandler.class);

    private final AuthFacade authFacade;
    private final RefreshCookieService refreshCookieService;
    private final GoogleLinkCookieService linkCookieService;
    private final GoogleOAuthProperties properties;

    public GoogleOAuthSuccessHandler(AuthFacade authFacade,
                                     RefreshCookieService refreshCookieService,
                                     GoogleLinkCookieService linkCookieService,
                                     GoogleOAuthProperties properties) {
        this.authFacade = authFacade;
        this.refreshCookieService = refreshCookieService;
        this.linkCookieService = linkCookieService;
        this.properties = properties;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {
        try {
            if (!(authentication instanceof OAuth2AuthenticationToken token)
                    || !(token.getPrincipal() instanceof OidcUser oidcUser)) {
                redirectFailure(request, response);
                return;
            }
            String email = oidcUser.getEmail();
            String fullName = StringUtils.hasText(oidcUser.getFullName())
                    ? oidcUser.getFullName() : fallbackName(email);
            GoogleProfile profile = new GoogleProfile(oidcUser.getSubject(), email, fullName,
                    Boolean.TRUE.equals(oidcUser.getEmailVerified()));
            GoogleLoginOutcome outcome = authFacade.beginGoogleLogin(profile);
            invalidateSession(request);

            if (outcome instanceof GoogleLoginOutcome.Authenticated authenticated) {
                AuthResult result = authFacade.issueSession(authenticated.user());
                response.addHeader(HttpHeaders.SET_COOKIE,
                        refreshCookieService.issue(result.refreshToken()));
                response.addHeader(HttpHeaders.SET_COOKIE, linkCookieService.clear());
                redirect(response, properties.clientUri("/login/google/callback").toString());
                return;
            }
            GoogleLoginOutcome.LinkRequired linkRequired = (GoogleLoginOutcome.LinkRequired) outcome;
            response.addHeader(HttpHeaders.SET_COOKIE, linkCookieService.issue(linkRequired.rawTicket()));
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookieService.clear());
            redirect(response, properties.clientUri("/login/google/link").toString());
        } catch (RuntimeException exception) {
            // Do not log provider claims, credentials or token responses.
            log.warn("Google login completion failed: {}", exception.getClass().getSimpleName());
            redirectFailure(request, response);
        }
    }

    private void redirectFailure(HttpServletRequest request, HttpServletResponse response) {
        invalidateSession(request);
        redirect(response, properties.clientUri("/login?googleError=oauth_failed").toString());
    }

    private static void redirect(HttpServletResponse response, String location) {
        response.setStatus(HttpStatus.FOUND.value());
        response.setHeader(HttpHeaders.LOCATION, location);
    }

    private static void invalidateSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    private static String fallbackName(String email) {
        if (!StringUtils.hasText(email)) {
            return "Utilisateur SANAA";
        }
        int separator = email.indexOf('@');
        return separator > 0 ? email.substring(0, separator) : email;
    }
}
