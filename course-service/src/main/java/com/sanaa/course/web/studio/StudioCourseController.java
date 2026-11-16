package com.sanaa.course.web.studio;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sanaa.course.security.CurrentUser;
import com.sanaa.course.service.CourseManagementService;
import com.sanaa.course.service.StudioQueryService;
import com.sanaa.course.web.dto.PageResponse;
import com.sanaa.course.web.dto.studio.CreateCourseRequest;
import com.sanaa.course.web.dto.studio.DiscountRequest;
import com.sanaa.course.web.dto.studio.ReplyRequest;
import com.sanaa.course.web.dto.studio.StudioCourseDetail;
import com.sanaa.course.web.dto.studio.StudioCourseSummary;
import com.sanaa.course.web.dto.studio.StudioReviewResponse;
import com.sanaa.course.web.dto.studio.StudioStatsResponse;
import com.sanaa.course.web.dto.studio.StudioStudentResponse;
import com.sanaa.course.web.dto.studio.UpdateCourseRequest;

/**
 * Publisher studio: courses, discounts, students, reviews and statistics.
 *
 * <p>A SUPER_ADMIN is not in the role list: platform oversight is audited and lives under
 * {@code /api/admin}. An INSTRUCTOR is limited to own courses by the services.
 */
@RestController
@RequestMapping("/api/studio")
@PreAuthorize("hasAnyRole('INSTRUCTOR', 'TENANT_ADMIN')")
public class StudioCourseController {

    private final CourseManagementService courseManagementService;
    private final StudioQueryService studioQueryService;

    public StudioCourseController(CourseManagementService courseManagementService,
                                  StudioQueryService studioQueryService) {
        this.courseManagementService = courseManagementService;
        this.studioQueryService = studioQueryService;
    }

    @GetMapping("/courses")
    public PageResponse<StudioCourseSummary> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return studioQueryService.list(CurrentUser.from(jwt), status, q, page, size);
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    public StudioCourseDetail create(@AuthenticationPrincipal Jwt jwt,
                                     @Valid @RequestBody CreateCourseRequest request) {
        return courseManagementService.create(CurrentUser.from(jwt), request);
    }

    @GetMapping("/courses/{id}")
    public StudioCourseDetail detail(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return studioQueryService.detail(CurrentUser.from(jwt), id);
    }

    @PutMapping("/courses/{id}")
    public StudioCourseDetail update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                     @Valid @RequestBody UpdateCourseRequest request) {
        return courseManagementService.update(CurrentUser.from(jwt), id, request);
    }

    @DeleteMapping("/courses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        courseManagementService.delete(CurrentUser.from(jwt), id);
    }

    @PostMapping("/courses/{id}/publish")
    public StudioCourseDetail publish(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return courseManagementService.publish(CurrentUser.from(jwt), id);
    }

    @PostMapping("/courses/{id}/archive")
    public StudioCourseDetail archive(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return courseManagementService.archive(CurrentUser.from(jwt), id);
    }

    @PutMapping("/courses/{id}/discount")
    public StudioCourseDetail updateDiscount(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable long id,
                                             @Valid @RequestBody DiscountRequest request) {
        return courseManagementService.updateDiscount(CurrentUser.from(jwt), id, request);
    }

    @DeleteMapping("/courses/{id}/discount")
    public StudioCourseDetail clearDiscount(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable long id) {
        return courseManagementService.clearDiscount(CurrentUser.from(jwt), id);
    }

    @GetMapping("/courses/{id}/students")
    public PageResponse<StudioStudentResponse> students(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return studioQueryService.students(CurrentUser.from(jwt), id, page, size);
    }

    @GetMapping("/courses/{id}/reviews")
    public PageResponse<StudioReviewResponse> reviews(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return studioQueryService.reviews(CurrentUser.from(jwt), id, page, size);
    }

    @PostMapping("/reviews/{reviewId}/reply")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reply(@AuthenticationPrincipal Jwt jwt, @PathVariable long reviewId,
                      @Valid @RequestBody ReplyRequest request) {
        courseManagementService.replyToReview(CurrentUser.from(jwt), reviewId, request);
    }

    @GetMapping("/stats")
    public StudioStatsResponse stats(@AuthenticationPrincipal Jwt jwt) {
        return studioQueryService.stats(CurrentUser.from(jwt));
    }
}
