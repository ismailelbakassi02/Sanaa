package com.sanaa.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.sanaa.auth.domain.MembershipRole;

/**
 * Team member creation by a publisher admin. There is deliberately no tenant field: the tenant is
 * always the one in the validated token.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AddTeamMemberRequest(
        @NotBlank @Size(min = 2, max = 120) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotNull MembershipRole role,
        @NotBlank @Size(max = 200) String temporaryPassword) {
}
