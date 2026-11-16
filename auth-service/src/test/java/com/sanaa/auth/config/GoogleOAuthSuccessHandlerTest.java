package com.sanaa.auth.config;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import com.sanaa.auth.service.AuthFacade;
import com.sanaa.auth.service.AuthResult;
import com.sanaa.auth.service.AuthenticatedUser;
import com.sanaa.auth.service.GoogleLinkCookieService;
import com.sanaa.auth.service.GoogleLoginOutcome;
import com.sanaa.auth.service.GoogleProfile;
import com.sanaa.auth.service.RefreshCookieService;
import com.sanaa.auth.web.dto.LoginResponse;
import com.sanaa.common.security.Role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleOAuthSuccessHandlerTest {

    @Test
    void createsOnlyTheSanaaRefreshCookieAndRedirectsToTheClient() throws Exception {
        AuthFacade facade = mock(AuthFacade.class);
        RefreshCookieService refreshCookies = mock(RefreshCookieService.class);
        GoogleLinkCookieService linkCookies = mock(GoogleLinkCookieService.class);
        GoogleOAuthProperties properties = properties();
        GoogleOAuthSuccessHandler handler = new GoogleOAuthSuccessHandler(
                facade, refreshCookies, linkCookies, properties);
        AuthenticatedUser user = new AuthenticatedUser(9, null, null, null,
                "verified@example.com", "Compte Google", Role.STUDENT, false);
        when(facade.beginGoogleLogin(any(GoogleProfile.class)))
                .thenReturn(new GoogleLoginOutcome.Authenticated(user));
        when(facade.issueSession(user))
                .thenReturn(new AuthResult(new LoginResponse("access-not-in-url", 900), "refresh-raw"));
        when(refreshCookies.issue("refresh-raw")).thenReturn("sanaa_refresh=cookie");
        when(linkCookies.clear()).thenReturn("sanaa_google_link=; Max-Age=0");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(request, response, authentication());

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location"))
                .isEqualTo("http://localhost:5173/login/google/callback")
                .doesNotContain("access-not-in-url", "refresh-raw");
        assertThat(response.getHeaders("Set-Cookie")).hasSize(2);
        assertThat(response.getHeaders("Set-Cookie").get(0)).isEqualTo("sanaa_refresh=cookie");
        assertThat(response.getHeaders("Set-Cookie").get(1))
                .startsWith("sanaa_google_link=; Max-Age=0");
        assertThat(session.isInvalid()).isTrue();
        verify(facade).beginGoogleLogin(new GoogleProfile(
                "google-subject", "verified@example.com", "Compte Google", true));
    }

    @Test
    void redirectsAnExistingEmailToPasswordConfirmationWithoutIssuingASession() throws Exception {
        AuthFacade facade = mock(AuthFacade.class);
        RefreshCookieService refreshCookies = mock(RefreshCookieService.class);
        GoogleLinkCookieService linkCookies = mock(GoogleLinkCookieService.class);
        GoogleOAuthSuccessHandler handler = new GoogleOAuthSuccessHandler(
                facade, refreshCookies, linkCookies, properties());
        when(facade.beginGoogleLogin(any(GoogleProfile.class)))
                .thenReturn(new GoogleLoginOutcome.LinkRequired("link-raw"));
        when(linkCookies.issue("link-raw")).thenReturn("sanaa_google_link=cookie");
        when(refreshCookies.clear()).thenReturn("sanaa_refresh=; Max-Age=0");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(request, response, authentication());

        assertThat(response.getHeader("Location"))
                .isEqualTo("http://localhost:5173/login/google/link");
        assertThat(response.getHeaders("Set-Cookie")).hasSize(2);
        assertThat(response.getHeaders("Set-Cookie").get(0)).isEqualTo("sanaa_google_link=cookie");
        assertThat(response.getHeaders("Set-Cookie").get(1))
                .startsWith("sanaa_refresh=; Max-Age=0");
    }

    private static OAuth2AuthenticationToken authentication() {
        Instant now = Instant.parse("2026-11-16T12:00:00Z");
        OidcIdToken idToken = new OidcIdToken("signed-id-token", now, now.plusSeconds(300), Map.of(
                "sub", "google-subject",
                "email", "verified@example.com",
                "email_verified", true,
                "name", "Compte Google"));
        DefaultOidcUser principal = new DefaultOidcUser(
                Set.of(new SimpleGrantedAuthority("OIDC_USER")), idToken);
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }

    private static GoogleOAuthProperties properties() {
        return new GoogleOAuthProperties(true, "client-id", "client-secret",
                URI.create("http://localhost:8080/api/auth/oauth2/callback/google"),
                URI.create("http://localhost:5173"), Duration.ofMinutes(10));
    }
}
