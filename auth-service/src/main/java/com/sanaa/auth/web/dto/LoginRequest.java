package com.sanaa.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login by email and password only. A tenant slug would reintroduce the v1 model and let a caller
 * probe which publisher an address belongs to.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LoginRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 200) String password) {
}
