package com.sanaa.course.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.course.domain.Course;
import com.sanaa.course.domain.CourseStatus;
import com.sanaa.course.domain.Lesson;
import com.sanaa.course.domain.Module;
import com.sanaa.course.error.BadRequestException;
import com.sanaa.course.error.ConflictException;
import com.sanaa.course.error.ForbiddenException;
import com.sanaa.course.error.NotFoundException;
import com.sanaa.course.repository.CourseRepository;
import com.sanaa.course.repository.LessonRepository;
import com.sanaa.course.repository.ModuleRepository;
import com.sanaa.course.security.CurrentUser;
import com.sanaa.course.web.dto.studio.LessonRequest;
import com.sanaa.course.web.dto.studio.ModuleRequest;
import com.sanaa.course.web.dto.studio.ReorderRequest;
import com.sanaa.course.web.dto.studio.StudioLessonResponse;
import com.sanaa.course.web.dto.studio.StudioModuleResponse;

/**
 * Curriculum writes: modules, lessons and their order.
 *
 * <p>Positions are server-assigned and contiguous from 1. A reorder is applied in two flushes: a
 * temporary offset frees every unique {@code position} slot, then the final values are written.
 * Without the first step, moving module 2 before module 1 would collide on the unique index.
 */
@Service
public class CurriculumService {

    /** Any offset larger than the maximum realistic number of children frees the slots. */
    private static final int TEMPORARY_OFFSET = 1_000_000;

    private final CourseRepository courseRepository;
    private final ModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;

    public CurriculumService(CourseRepository courseRepository,
                             ModuleRepository moduleRepository,
                             LessonRepository lessonRepository) {
        this.courseRepository = courseRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
    }

    @Transactional
    public StudioModuleResponse addModule(CurrentUser user, long courseId, ModuleRequest request) {
        Course course = loadCourseForWrite(courseId, user);
        requireEditable(course);
        int position = moduleRepository.findMaxPosition(courseId) + 1;
        Module module = moduleRepository.saveAndFlush(
                new Module(courseId, request.title().trim(), position));
        return new StudioModuleResponse(module.getId(), module.getTitle(), module.getPosition(),
                List.of());
    }

    @Transactional
    public StudioModuleResponse renameModule(CurrentUser user, long moduleId,
                                             ModuleRequest request) {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new NotFoundException("Module not found."));
        Course course = loadCourseForWrite(module.getCourseId(), user);
        requireEditable(course);
        module.rename(request.title().trim());
        moduleRepository.saveAndFlush(module);
        List<StudioLessonResponse> lessons = lessonRepository
                .findByCourseIdOrderByPositionAsc(course.getId()).stream()
                .filter(lesson -> lesson.getModuleId().equals(moduleId))
                .map(StudioLessonResponse::from)
                .toList();
        return new StudioModuleResponse(module.getId(), module.getTitle(), module.getPosition(),
                lessons);
    }

    @Transactional
    public void deleteModule(CurrentUser user, long moduleId) {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new NotFoundException("Module not found."));
        Course course = loadCourseForWrite(module.getCourseId(), user);
        requireEditable(course);
        lessonRepository.deleteByModuleId(moduleId);
        moduleRepository.delete(module);
    }

    @Transactional
    public List<StudioModuleResponse> reorderModules(CurrentUser user, long courseId,
                                                     ReorderRequest request) {
        Course course = loadCourseForWrite(courseId, user);
        requireEditable(course);
        List<Module> modules = moduleRepository.findByCourseIdOrderByPositionAsc(courseId);
        Map<Long, Module> byId = new LinkedHashMap<>();
        modules.forEach(module -> byId.put(module.getId(), module));
        requireSameIds(byId.keySet(), request.ids(), "module");

        for (int index = 0; index < request.ids().size(); index++) {
            byId.get(request.ids().get(index)).setPosition(TEMPORARY_OFFSET + index);
        }
        moduleRepository.flush();
        for (int index = 0; index < request.ids().size(); index++) {
            byId.get(request.ids().get(index)).setPosition(index + 1);
        }
        moduleRepository.flush();

        List<Module> ordered = moduleRepository.findByCourseIdOrderByPositionAsc(courseId);
        Map<Long, List<StudioLessonResponse>> lessons = new LinkedHashMap<>();
        lessonRepository.findByCourseIdOrderByPositionAsc(courseId)
                .forEach(lesson -> lessons
                        .computeIfAbsent(lesson.getModuleId(), key -> new java.util.ArrayList<>())
                        .add(StudioLessonResponse.from(lesson)));
        return ordered.stream()
                .map(module -> new StudioModuleResponse(module.getId(), module.getTitle(),
                        module.getPosition(), lessons.getOrDefault(module.getId(), List.of())))
                .toList();
    }

    @Transactional
    public StudioLessonResponse addLesson(CurrentUser user, long moduleId, LessonRequest request) {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new NotFoundException("Module not found."));
        Course course = loadCourseForWrite(module.getCourseId(), user);
        requireEditable(course);
        int position = lessonRepository.findMaxPosition(moduleId) + 1;
        Lesson lesson = lessonRepository.saveAndFlush(new Lesson(
                course.getId(),
                module.getId(),
                request.title().trim(),
                normalize(request.description()),
                position,
                request.durationSeconds(),
                request.freePreview()));
        return StudioLessonResponse.from(lesson);
    }

    @Transactional
    public StudioLessonResponse updateLesson(CurrentUser user, long lessonId,
                                             LessonRequest request) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new NotFoundException("Lesson not found."));
        Course course = loadCourseForWrite(lesson.getCourseId(), user);
        requireEditable(course);
        lesson.update(request.title().trim(), normalize(request.description()),
                request.durationSeconds(), request.freePreview());
        lessonRepository.saveAndFlush(lesson);
        return StudioLessonResponse.from(lesson);
    }

    @Transactional
    public void deleteLesson(CurrentUser user, long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new NotFoundException("Lesson not found."));
        Course course = loadCourseForWrite(lesson.getCourseId(), user);
        requireEditable(course);
        lessonRepository.delete(lesson);
    }

    @Transactional
    public List<StudioLessonResponse> reorderLessons(CurrentUser user, long moduleId,
                                                     ReorderRequest request) {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new NotFoundException("Module not found."));
        Course course = loadCourseForWrite(module.getCourseId(), user);
        requireEditable(course);

        Map<Long, Lesson> byId = new LinkedHashMap<>();
        lessonRepository.findByCourseIdOrderByPositionAsc(course.getId()).stream()
                .filter(lesson -> lesson.getModuleId().equals(moduleId))
                .forEach(lesson -> byId.put(lesson.getId(), lesson));
        requireSameIds(byId.keySet(), request.ids(), "lesson");

        for (int index = 0; index < request.ids().size(); index++) {
            byId.get(request.ids().get(index)).setPosition(TEMPORARY_OFFSET + index);
        }
        lessonRepository.flush();
        for (int index = 0; index < request.ids().size(); index++) {
            byId.get(request.ids().get(index)).setPosition(index + 1);
        }
        lessonRepository.flush();

        return lessonRepository.findByCourseIdOrderByPositionAsc(course.getId()).stream()
                .filter(lesson -> lesson.getModuleId().equals(moduleId))
                .map(StudioLessonResponse::from)
                .toList();
    }

    private Course loadCourseForWrite(long courseId, CurrentUser user) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found."));
        return switch (user.role()) {
            case TENANT_ADMIN -> course;
            case INSTRUCTOR -> {
                if (course.getInstructorId() == user.userId()) {
                    yield course;
                }
                throw new NotFoundException("Course not found.");
            }
            default -> throw new ForbiddenException("Access denied.");
        };
    }

    private static void requireEditable(Course course) {
        if (course.getStatus() == CourseStatus.ARCHIVED) {
            throw new ConflictException("Archived courses are read-only.");
        }
        if (course.getStatus() == CourseStatus.SUSPENDED) {
            throw new ConflictException("Suspended courses are read-only.");
        }
    }

    private static void requireSameIds(java.util.Set<Long> current, List<Long> requested,
                                       String label) {
        if (current.size() != requested.size()
                || requested.stream().distinct().count() != requested.size()
                || !current.containsAll(requested)) {
            throw new BadRequestException(
                    "The order must contain every " + label + " of the parent exactly once.");
        }
        if (requested.stream().anyMatch(Objects::isNull)) {
            throw new BadRequestException("The order contains a null id.");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
