package com.sanaa.course.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sanaa.course.security.CurrentUser;
import com.sanaa.course.service.EnrollmentQueryService;
import com.sanaa.course.web.dto.MyEnrollmentResponse;

/**
 * Read-only enrollment API. Creation belongs to the payment-approved Kafka consumer, so there is
 * deliberately no POST endpoint here.
 */
@RestController
@RequestMapping("/api/enrollments")
@PreAuthorize("hasRole('STUDENT')")
public class EnrollmentController {

    private final EnrollmentQueryService enrollmentQueryService;

    public EnrollmentController(EnrollmentQueryService enrollmentQueryService) {
        this.enrollmentQueryService = enrollmentQueryService;
    }

    @GetMapping("/me")
    public List<MyEnrollmentResponse> myEnrollments(@AuthenticationPrincipal Jwt jwt) {
        return enrollmentQueryService.myEnrollments(CurrentUser.from(jwt));
    }
}
