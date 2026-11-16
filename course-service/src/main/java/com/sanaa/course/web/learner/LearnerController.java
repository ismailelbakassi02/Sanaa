package com.sanaa.course.web.learner;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sanaa.course.security.CurrentUser;
import com.sanaa.course.service.LearnerService;
import com.sanaa.course.web.dto.learner.LearnerEnrollmentResponse;
import com.sanaa.course.web.dto.learner.LearnerHomeResponse;
import com.sanaa.course.web.dto.learner.MyReviewResponse;
import com.sanaa.course.web.dto.learner.PurchaseInfoResponse;
import com.sanaa.course.web.dto.learner.ReviewRequest;

/**
 * The learner view. Every endpoint is scoped to the JWT subject; SUPER_ADMIN is excluded because
 * platform oversight of personal data is a separate, audited endpoint.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR', 'TENANT_ADMIN')")
public class LearnerController {

    private final LearnerService learnerService;

    public LearnerController(LearnerService learnerService) {
        this.learnerService = learnerService;
    }

    @GetMapping("/learner/home")
    public LearnerHomeResponse home(@AuthenticationPrincipal Jwt jwt) {
        return learnerService.home(CurrentUser.from(jwt));
    }

    @GetMapping("/learner/enrollments")
    public List<LearnerEnrollmentResponse> enrollments(@AuthenticationPrincipal Jwt jwt) {
        return learnerService.myEnrollments(CurrentUser.from(jwt));
    }

    /**
     * My own review of a course, or 404 when I have not reviewed it. The subject comes from the
     * token, so this can never return another learner's review.
     */
    @GetMapping("/learner/courses/{courseId}/my-review")
    public MyReviewResponse myReview(@AuthenticationPrincipal Jwt jwt,
                                     @PathVariable long courseId) {
        return learnerService.myReview(CurrentUser.from(jwt), courseId);
    }

    /** Trusted price and tenant lookup used by payment-service before it creates a payment. */
    @GetMapping("/learner/courses/{courseId}/purchase-info")
    public PurchaseInfoResponse purchaseInfo(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable long courseId) {
        return learnerService.purchaseInfo(CurrentUser.from(jwt), courseId);
    }

    @PostMapping("/courses/{courseId}/enroll")
    @ResponseStatus(HttpStatus.CREATED)
    public LearnerEnrollmentResponse enroll(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable long courseId) {
        return learnerService.enrollFree(CurrentUser.from(jwt), courseId);
    }

    @PostMapping("/courses/{courseId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public MyReviewResponse createReview(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable long courseId,
                                         @Valid @RequestBody ReviewRequest request) {
        return learnerService.upsertReview(CurrentUser.from(jwt), courseId, request);
    }

    @PutMapping("/courses/{courseId}/reviews")
    public MyReviewResponse updateReview(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable long courseId,
                                         @Valid @RequestBody ReviewRequest request) {
        return learnerService.upsertReview(CurrentUser.from(jwt), courseId, request);
    }
}
