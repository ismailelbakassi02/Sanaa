package com.sanaa.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Public learner registration. There is no tenant slug: accounts are global. Byte length of the
 * password is checked in the service because Bean Validation counts characters, not UTF-8 bytes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 200) String password,
        @NotBlank @Size(min = 2, max = 120) String fullName) {
}
