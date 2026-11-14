package com.sanaa.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;

import com.sanaa.auth.domain.MembershipStatus;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateTeamMemberStatusRequest(@NotNull MembershipStatus status) {
}
