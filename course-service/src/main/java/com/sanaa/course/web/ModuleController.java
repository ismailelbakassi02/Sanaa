package com.sanaa.course.web;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sanaa.course.security.CurrentUser;
import com.sanaa.course.service.CourseManagementService;
import com.sanaa.course.web.dto.AddLessonRequest;
import com.sanaa.course.web.dto.LessonResponse;

@RestController
@RequestMapping("/api/modules")
@PreAuthorize("hasAnyRole('INSTRUCTOR', 'TENANT_ADMIN')")
public class ModuleController {

    private final CourseManagementService courseManagementService;

    public ModuleController(CourseManagementService courseManagementService) {
        this.courseManagementService = courseManagementService;
    }

    @PostMapping("/{id}/lessons")
    @ResponseStatus(HttpStatus.CREATED)
    public LessonResponse addLesson(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                    @Valid @RequestBody AddLessonRequest request) {
        return courseManagementService.addLesson(CurrentUser.from(jwt), id, request);
    }
}
