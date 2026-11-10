package com.sanaa.payment.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Rejection reason shown to the student in the rejection notification.
 */
public record RejectPaymentRequest(
        @NotBlank(message = "A rejection reason is required.")
        @Size(min = 3, max = 500, message = "The reason must be between 3 and 500 characters.")
        String reason) {
}
