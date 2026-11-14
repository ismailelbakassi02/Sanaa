package com.sanaa.auth.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sanaa.auth.error.ForbiddenException;
import com.sanaa.auth.service.TeamService;
import com.sanaa.auth.web.dto.AddTeamMemberRequest;
import com.sanaa.auth.web.dto.ResetTeamPasswordRequest;
import com.sanaa.auth.web.dto.TeamMemberResponse;
import com.sanaa.auth.web.dto.UpdateTeamMemberStatusRequest;
import com.sanaa.common.security.JwtClaims;

/**
 * Publisher team. The tenant always comes from the validated JWT; there is no tenant parameter.
 */
@RestController
@RequestMapping("/api/auth/team")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    public List<TeamMemberResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return teamService.list(tenantId(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeamMemberResponse add(@AuthenticationPrincipal Jwt jwt,
                                  @Valid @RequestBody AddTeamMemberRequest request) {
        return teamService.add(tenantId(jwt), request);
    }

    @PatchMapping("/{userId}/status")
    public TeamMemberResponse updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long userId,
            @Valid @RequestBody UpdateTeamMemberStatusRequest request) {
        return teamService.updateStatus(tenantId(jwt), userId, request.status(),
                com.sanaa.auth.service.AuthFacade.userId(jwt));
    }

    @PostMapping("/{userId}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@AuthenticationPrincipal Jwt jwt,
                              @PathVariable long userId,
                              @Valid @RequestBody ResetTeamPasswordRequest request) {
        teamService.resetPassword(tenantId(jwt), userId, request.temporaryPassword());
    }

    private static String tenantId(Jwt jwt) {
        String tenantId = jwt.getClaimAsString(JwtClaims.TENANT_ID);
        if (tenantId == null) {
            throw new ForbiddenException("A publisher admin session is required.");
        }
        return tenantId;
    }
}
