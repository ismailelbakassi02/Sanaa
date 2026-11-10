package com.sanaa.course.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.course.domain.Course;
import com.sanaa.course.domain.CourseStatus;
import com.sanaa.course.domain.Lesson;
import com.sanaa.course.domain.Module;
import com.sanaa.course.error.ConflictException;
import com.sanaa.course.error.ForbiddenException;
import com.sanaa.course.error.NotFoundException;
import com.sanaa.course.error.UnprocessableEntityException;
import com.sanaa.course.repository.CourseRepository;
import com.sanaa.course.repository.LessonRepository;
import com.sanaa.course.repository.ModuleRepository;
import com.sanaa.course.security.CurrentUser;
import com.sanaa.course.web.dto.AddLessonRequest;
import com.sanaa.course.web.dto.AddModuleRequest;
import com.sanaa.course.web.dto.CourseDetailResponse;
import com.sanaa.course.web.dto.CreateCourseRequest;
import com.sanaa.course.web.dto.LessonResponse;
import com.sanaa.course.web.dto.ModuleResponse;
import com.sanaa.course.web.dto.UpdateCourseRequest;

@Service
public class CourseManagementService {

    private static final String DEFAULT_CURRENCY = "MAD";

    private final CourseRepository courseRepository;
    private final ModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final CourseQueryService courseQueryService;

    public CourseManagementService(CourseRepository courseRepository,
                                   ModuleRepository moduleRepository,
                                   LessonRepository lessonRepository,
                                   CourseQueryService courseQueryService) {
        this.courseRepository = courseRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.courseQueryService = courseQueryService;
    }

    @Transactional
    public CourseDetailResponse create(CurrentUser user, CreateCourseRequest request) {
        Course course = new Course(
                request.title(),
                normalizeDescription(request.description()),
                request.price(),
                normalizeCurrency(request.currency()),
                user.userId());
        courseRepository.saveAndFlush(course);
        return courseQueryService.view(course);
    }

    @Transactional
    public CourseDetailResponse update(CurrentUser user, long courseId, UpdateCourseRequest request) {
        Course course = loadForWrite(courseId, user);
        requireEditable(course);
        if (course.getVersion() != request.version()) {
            throw new ConflictException(
                    "The course was modified by another request. Reload it and retry.");
        }
        course.updateDetails(
                request.title(),
                normalizeDescription(request.description()),
                request.price(),
                normalizeCurrency(request.currency()));
        courseRepository.saveAndFlush(course);
        return courseQueryService.view(course);
    }

    @Transactional
    public CourseDetailResponse publish(CurrentUser user, long courseId) {
        Course course = loadForWrite(courseId, user);
        if (course.getStatus() == CourseStatus.ARCHIVED) {
            throw new ConflictException("Archived courses cannot be published.");
        }
        if (course.getStatus() == CourseStatus.PUBLISHED) {
            return courseQueryService.view(course);
        }
        if (lessonRepository.countByCourseId(courseId) == 0) {
            throw new UnprocessableEntityException(
                    "A course needs at least one lesson before it can be published.");
        }
        course.publish();
        courseRepository.saveAndFlush(course);
        return courseQueryService.view(course);
    }

    @Transactional
    public CourseDetailResponse archive(CurrentUser user, long courseId) {
        Course course = loadForWrite(courseId, user);
        if (course.getStatus() != CourseStatus.ARCHIVED) {
            course.archive();
            courseRepository.saveAndFlush(course);
        }
        return courseQueryService.view(course);
    }

    @Transactional
    public ModuleResponse addModule(CurrentUser user, long courseId, AddModuleRequest request) {
        Course course = loadForWrite(courseId, user);
        requireEditable(course);

        // Positions are always server-assigned. The (course_id, position) unique index is the
        // final guard; a concurrent collision becomes a 409 through the exception handler.
        int position = moduleRepository.findMaxPosition(courseId) + 1;
        Module module = new Module(course.getId(), request.title(), position);
        moduleRepository.saveAndFlush(module);
        return ModuleResponse.from(module);
    }

    @Transactional
    public LessonResponse addLesson(CurrentUser user, long moduleId, AddLessonRequest request) {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new NotFoundException("Module not found."));
        Course course = loadForWrite(module.getCourseId(), user);
        requireEditable(course);

        int position = lessonRepository.findMaxPosition(moduleId) + 1;
        Lesson lesson = new Lesson(
                course.getId(),
                module.getId(),
                request.title(),
                position,
                request.durationSeconds(),
                Boolean.TRUE.equals(request.freePreview()));
        lessonRepository.saveAndFlush(lesson);
        return LessonResponse.from(lesson);
    }

    /**
     * Ownership rules shared by update, publish, archive and content creation.
     *
     * <p>A tenant administrator may edit any course of the tenant. An instructor may edit only
     * their own courses. A non-owner instructor who can see the course (it is PUBLISHED) gets 403;
     * one who cannot see it (DRAFT or ARCHIVED) gets 404 so its existence is not disclosed.
     */
    private Course loadForWrite(long courseId, CurrentUser user) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found."));

        return switch (user.role()) {
            case TENANT_ADMIN -> course;
            case INSTRUCTOR -> {
                if (course.getInstructorId() == user.userId()) {
                    yield course;
                }
                if (course.getStatus() == CourseStatus.PUBLISHED) {
                    throw new ForbiddenException(
                            "Only the course owner or a tenant administrator can modify this course.");
                }
                throw new NotFoundException("Course not found.");
            }
            default -> throw new ForbiddenException("Access denied.");
        };
    }

    private static void requireEditable(Course course) {
        if (course.getStatus() == CourseStatus.ARCHIVED) {
            throw new ConflictException("Archived courses cannot be edited.");
        }
    }

    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description;
    }

    private static String normalizeCurrency(String currency) {
        return currency == null ? DEFAULT_CURRENCY : currency;
    }
}
