package com.sanaa.course.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.common.security.Role;
import com.sanaa.course.domain.Course;
import com.sanaa.course.domain.CourseStatus;
import com.sanaa.course.domain.Lesson;
import com.sanaa.course.domain.Module;
import com.sanaa.course.error.ForbiddenException;
import com.sanaa.course.error.NotFoundException;
import com.sanaa.course.repository.CourseRepository;
import com.sanaa.course.repository.LessonRepository;
import com.sanaa.course.repository.ModuleRepository;
import com.sanaa.course.security.CurrentUser;
import com.sanaa.course.web.dto.CourseDetailResponse;
import com.sanaa.course.web.dto.CourseSummary;
import com.sanaa.course.web.dto.LessonSummary;
import com.sanaa.course.web.dto.ModuleSummary;
import com.sanaa.course.web.dto.PageResponse;

@Service
public class CourseQueryService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    /** Fixed ordering: newest first, with id as a stable tie-breaker for equal timestamps. */
    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final CourseRepository courseRepository;
    private final ModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;

    public CourseQueryService(CourseRepository courseRepository,
                              ModuleRepository moduleRepository,
                              LessonRepository lessonRepository) {
        this.courseRepository = courseRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseSummary> list(CurrentUser user, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = normalizeSize(size);
        Pageable pageable = PageRequest.of(safePage, safeSize, NEWEST_FIRST);

        Page<Course> courses = switch (user.role()) {
            case STUDENT -> courseRepository.findByStatus(CourseStatus.PUBLISHED, pageable);
            case INSTRUCTOR -> courseRepository.findByInstructorIdOrStatus(
                    user.userId(), CourseStatus.PUBLISHED, pageable);
            case TENANT_ADMIN -> courseRepository.findAll(pageable);
            default -> throw new ForbiddenException("Access denied.");
        };

        List<CourseSummary> items = courses.getContent().stream()
                .map(CourseSummary::from)
                .toList();
        return new PageResponse<>(items, safePage, safeSize,
                courses.getTotalElements(), courses.getTotalPages());
    }

    @Transactional(readOnly = true)
    public CourseDetailResponse detail(CurrentUser user, long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found."));
        if (!canView(course, user)) {
            // A resource the caller may not see is reported as absent, so ids do not leak.
            throw new NotFoundException("Course not found.");
        }
        return view(course);
    }

    /**
     * Builds the detail representation. Callers are responsible for the authorization decision;
     * management methods call it after applying the ownership rules.
     */
    @Transactional(readOnly = true)
    public CourseDetailResponse view(Course course) {
        List<Module> modules = moduleRepository.findByCourseIdOrderByPositionAsc(course.getId());
        List<Lesson> lessons = lessonRepository.findByCourseIdOrderByPositionAsc(course.getId());

        Map<Long, List<LessonSummary>> lessonsByModule = lessons.stream()
                .collect(Collectors.groupingBy(
                        Lesson::getModuleId,
                        LinkedHashMap::new,
                        Collectors.mapping(LessonSummary::from, Collectors.toList())));

        List<ModuleSummary> moduleSummaries = modules.stream()
                .map(module -> new ModuleSummary(
                        module.getId(),
                        module.getTitle(),
                        module.getPosition(),
                        lessonsByModule.getOrDefault(module.getId(), List.of())))
                .toList();

        return new CourseDetailResponse(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getPrice(),
                course.getCurrency(),
                course.getStatus().name(),
                course.getInstructorId(),
                course.getVersion(),
                course.getCreatedAt(),
                course.getUpdatedAt(),
                moduleSummaries);
    }

    private static boolean canView(Course course, CurrentUser user) {
        return switch (user.role()) {
            case TENANT_ADMIN -> true;
            case INSTRUCTOR -> course.getInstructorId() == user.userId()
                    || course.getStatus() == CourseStatus.PUBLISHED;
            case STUDENT -> course.getStatus() == CourseStatus.PUBLISHED;
            case SUPER_ADMIN -> false;
        };
    }

    private static int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
