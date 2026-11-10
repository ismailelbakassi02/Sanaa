package com.sanaa.payment.web;

import jakarta.validation.Valid;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sanaa.payment.domain.PaymentMethod;
import com.sanaa.payment.domain.PaymentStatus;
import com.sanaa.payment.security.CurrentUser;
import com.sanaa.payment.service.PaymentQueryService;
import com.sanaa.payment.service.PaymentReviewService;
import com.sanaa.payment.service.PaymentSubmissionService;
import com.sanaa.payment.service.ReceiptFile;
import com.sanaa.payment.web.dto.PageResponse;
import com.sanaa.payment.web.dto.PaymentResponse;
import com.sanaa.payment.web.dto.RejectPaymentRequest;

/**
 * Payment endpoints.
 *
 * <p>INSTRUCTOR and SUPER_ADMIN are absent from every role list on purpose: instructors do not
 * handle money in this product and a platform administrator owns tenants, not tenant payments, so
 * both get 403 before any handler runs.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentSubmissionService submissionService;
    private final PaymentQueryService queryService;
    private final PaymentReviewService reviewService;

    public PaymentController(PaymentSubmissionService submissionService,
                             PaymentQueryService queryService,
                             PaymentReviewService reviewService) {
        this.submissionService = submissionService;
        this.queryService = queryService;
        this.reviewService = reviewService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STUDENT')")
    public PaymentResponse create(@AuthenticationPrincipal Jwt jwt,
                                  @RequestParam long courseId,
                                  @RequestParam PaymentMethod method,
                                  @RequestPart("receipt") MultipartFile receipt) {
        return submissionService.submit(
                CurrentUser.from(jwt),
                courseId,
                method,
                receipt,
                "Bearer " + jwt.getTokenValue());
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    public PageResponse<PaymentResponse> mine(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return queryService.listMine(CurrentUser.from(jwt), page, size);
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public PageResponse<PaymentResponse> list(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(required = false) PaymentStatus status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return queryService.list(CurrentUser.from(jwt), status, page, size);
    }

    @GetMapping("/{id}/receipt")
    @PreAuthorize("hasAnyRole('STUDENT', 'TENANT_ADMIN')")
    public ResponseEntity<ByteArrayResource> receipt(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable long id) {
        ReceiptFile file = queryService.downloadReceipt(CurrentUser.from(jwt), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ByteArrayResource(file.content()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public PaymentResponse approve(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return reviewService.approve(CurrentUser.from(jwt), id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public PaymentResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                  @Valid @RequestBody RejectPaymentRequest request) {
        return reviewService.reject(CurrentUser.from(jwt), id, request.reason());
    }
}
