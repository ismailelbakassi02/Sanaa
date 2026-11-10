package com.sanaa.course.web;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import com.sanaa.course.service.CourseQueryService;
import com.sanaa.course.web.dto.AddModuleRequest;
import com.sanaa.course.web.dto.CourseDetailResponse;
import com.sanaa.course.web.dto.CourseSummary;
import com.sanaa.course.web.dto.CreateCourseRequest;
import com.sanaa.course.web.dto.ModuleResponse;
import com.sanaa.course.web.dto.PageResponse;
import com.sanaa.course.web.dto.UpdateCourseRequest;

/**
 * Course endpoints. SUPER_ADMIN is intentionally absent from every role list: the platform owns
 * tenants, not tenant content, so it receives 403 before any handler runs.
 */
@RestController
@RequestMapping("/api/courses")
@PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR', 'TENANT_ADMIN')")
public class CourseController {

    private final CourseQueryService courseQueryService;
    private final CourseManagementService courseManagementService;

    public CourseController(CourseQueryService courseQueryService,
                            CourseManagementService courseManagementService) {
        this.courseQueryService = courseQueryService;
        this.courseManagementService = courseManagementService;
    }

    @GetMapping
    public PageResponse<CourseSummary> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return courseQueryService.list(CurrentUser.from(jwt), page, size);
    }

    @GetMapping("/{id}")
    public CourseDetailResponse detail(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return courseQueryService.detail(CurrentUser.from(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'TENANT_ADMIN')")
    public CourseDetailResponse create(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody CreateCourseRequest request) {
        return courseManagementService.create(CurrentUser.from(jwt), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'TENANT_ADMIN')")
    public CourseDetailResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                       @Valid @RequestBody UpdateCourseRequest request) {
        return courseManagementService.update(CurrentUser.from(jwt), id, request);
    }

    @PatchMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'TENANT_ADMIN')")
    public CourseDetailResponse publish(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return courseManagementService.publish(CurrentUser.from(jwt), id);
    }

    @PatchMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'TENANT_ADMIN')")
    public CourseDetailResponse archive(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return courseManagementService.archive(CurrentUser.from(jwt), id);
    }

    @PostMapping("/{id}/modules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'TENANT_ADMIN')")
    public ModuleResponse addModule(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                    @Valid @RequestBody AddModuleRequest request) {
        return courseManagementService.addModule(CurrentUser.from(jwt), id, request);
    }
}
