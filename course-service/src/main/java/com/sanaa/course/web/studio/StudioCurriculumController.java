package com.sanaa.course.web.studio;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sanaa.course.security.CurrentUser;
import com.sanaa.course.service.CurriculumService;
import com.sanaa.course.web.dto.studio.LessonRequest;
import com.sanaa.course.web.dto.studio.ModuleRequest;
import com.sanaa.course.web.dto.studio.ReorderRequest;
import com.sanaa.course.web.dto.studio.StudioLessonResponse;
import com.sanaa.course.web.dto.studio.StudioModuleResponse;

/** Modules, lessons and their order inside the studio. */
@RestController
@RequestMapping("/api/studio")
@PreAuthorize("hasAnyRole('INSTRUCTOR', 'TENANT_ADMIN')")
public class StudioCurriculumController {

    private final CurriculumService curriculumService;

    public StudioCurriculumController(CurriculumService curriculumService) {
        this.curriculumService = curriculumService;
    }

    @PostMapping("/courses/{courseId}/modules")
    @ResponseStatus(HttpStatus.CREATED)
    public StudioModuleResponse addModule(@AuthenticationPrincipal Jwt jwt,
                                          @PathVariable long courseId,
                                          @Valid @RequestBody ModuleRequest request) {
        return curriculumService.addModule(CurrentUser.from(jwt), courseId, request);
    }

    @PutMapping("/modules/{moduleId}")
    public StudioModuleResponse renameModule(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable long moduleId,
                                             @Valid @RequestBody ModuleRequest request) {
        return curriculumService.renameModule(CurrentUser.from(jwt), moduleId, request);
    }

    @DeleteMapping("/modules/{moduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteModule(@AuthenticationPrincipal Jwt jwt, @PathVariable long moduleId) {
        curriculumService.deleteModule(CurrentUser.from(jwt), moduleId);
    }

    @PutMapping("/courses/{courseId}/modules/order")
    public List<StudioModuleResponse> reorderModules(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable long courseId,
                                                     @Valid @RequestBody ReorderRequest request) {
        return curriculumService.reorderModules(CurrentUser.from(jwt), courseId, request);
    }

    @PostMapping("/modules/{moduleId}/lessons")
    @ResponseStatus(HttpStatus.CREATED)
    public StudioLessonResponse addLesson(@AuthenticationPrincipal Jwt jwt,
                                          @PathVariable long moduleId,
                                          @Valid @RequestBody LessonRequest request) {
        return curriculumService.addLesson(CurrentUser.from(jwt), moduleId, request);
    }

    @PutMapping("/lessons/{lessonId}")
    public StudioLessonResponse updateLesson(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable long lessonId,
                                             @Valid @RequestBody LessonRequest request) {
        return curriculumService.updateLesson(CurrentUser.from(jwt), lessonId, request);
    }

    @DeleteMapping("/lessons/{lessonId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLesson(@AuthenticationPrincipal Jwt jwt, @PathVariable long lessonId) {
        curriculumService.deleteLesson(CurrentUser.from(jwt), lessonId);
    }

    @PutMapping("/modules/{moduleId}/lessons/order")
    public List<StudioLessonResponse> reorderLessons(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable long moduleId,
                                                     @Valid @RequestBody ReorderRequest request) {
        return curriculumService.reorderLessons(CurrentUser.from(jwt), moduleId, request);
    }
}
