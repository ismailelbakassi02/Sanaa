package com.sanaa.course.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.common.security.Role;
import com.sanaa.course.domain.Category;
import com.sanaa.course.domain.Course;
import com.sanaa.course.domain.CourseStatus;
import com.sanaa.course.domain.Enrollment;
import com.sanaa.course.domain.EnrollmentStatus;
import com.sanaa.course.domain.Lesson;
import com.sanaa.course.domain.Module;
import com.sanaa.course.domain.Review;
import com.sanaa.course.error.BadRequestException;
import com.sanaa.course.error.NotFoundException;
import com.sanaa.course.repository.CategoryRepository;
import com.sanaa.course.repository.CourseRepository;
import com.sanaa.course.repository.CourseTagRepository;
import com.sanaa.course.repository.EnrollmentRepository;
import com.sanaa.course.repository.LessonRepository;
import com.sanaa.course.repository.ModuleRepository;
import com.sanaa.course.repository.ReviewRepository;
import com.sanaa.course.security.CurrentUser;
import com.sanaa.course.web.dto.PageResponse;
import com.sanaa.course.web.dto.studio.StudioCourseDetail;
import com.sanaa.course.web.dto.studio.StudioCourseSummary;
import com.sanaa.course.web.dto.studio.StudioLessonResponse;
import com.sanaa.course.web.dto.studio.StudioModuleResponse;
import com.sanaa.course.web.dto.studio.StudioReviewResponse;
import com.sanaa.course.web.dto.studio.StudioStatsResponse;
import com.sanaa.course.web.dto.studio.StudioStudentResponse;

/**
 * Reads of the publisher studio. An INSTRUCTOR is always scoped to their own courses; a
 * TENANT_ADMIN sees the whole tenant. The scope is applied in the query, not after loading, so a
 * wrong id can never return another instructor's data.
 */
@Service
public class StudioQueryService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));

    private final CourseRepository courseRepository;
    private final ModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseTagRepository courseTagRepository;
    private final CategoryRepository categoryRepository;
    private final ReviewRepository reviewRepository;
    private final Clock clock;

    public StudioQueryService(CourseRepository courseRepository,
                              ModuleRepository moduleRepository,
                              LessonRepository lessonRepository,
                              EnrollmentRepository enrollmentRepository,
                              CourseTagRepository courseTagRepository,
                              CategoryRepository categoryRepository,
                              ReviewRepository reviewRepository,
                              Clock clock) {
        this.courseRepository = courseRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.courseTagRepository = courseTagRepository;
        this.categoryRepository = categoryRepository;
        this.reviewRepository = reviewRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<StudioCourseSummary> list(CurrentUser user, String status, String q,
                                                  int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = normalizeSize(size);
        Pageable pageable = PageRequest.of(safePage, safeSize, NEWEST_FIRST);
        Long instructorId = user.role() == Role.INSTRUCTOR ? user.userId() : null;

        Page<Course> courses = courseRepository.searchForStudio(
                instructorId, parseStatus(status), normalizeSearch(q), pageable);

        Map<Long, String> categoryNames = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Category::getNameFr));
        List<StudioCourseSummary> items = courses.getContent().stream()
                .map(course -> summary(course, categoryNames.get(course.getCategoryId())))
                .toList();
        return new PageResponse<>(items, safePage, safeSize,
                courses.getTotalElements(), courses.getTotalPages());
    }

    @Transactional(readOnly = true)
    public StudioCourseDetail detail(CurrentUser user, long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found."));
        authorize(course, user);
        return buildDetail(course);
    }

    /**
     * Builds the studio representation for a caller already authorized by the caller. Also used by
     * {@link CourseManagementService} inside its write transaction, hence public.
     */
    @Transactional(readOnly = true)
    public StudioCourseDetail buildDetail(Course course) {
        List<Module> modules = moduleRepository.findByCourseIdOrderByPositionAsc(course.getId());
        List<Lesson> lessons = lessonRepository.findByCourseIdOrderByPositionAsc(course.getId());
        Map<Long, List<StudioLessonResponse>> lessonsByModule = lessons.stream()
                .collect(Collectors.groupingBy(
                        Lesson::getModuleId,
                        LinkedHashMap::new,
                        Collectors.mapping(StudioLessonResponse::from, Collectors.toList())));

        List<StudioModuleResponse> moduleResponses = modules.stream()
                .map(module -> new StudioModuleResponse(
                        module.getId(),
                        module.getTitle(),
                        module.getPosition(),
                        lessonsByModule.getOrDefault(module.getId(), List.of())))
                .toList();

        String categoryName = course.getCategoryId() == null ? null
                : categoryRepository.findById(course.getCategoryId())
                        .map(Category::getNameFr).orElse(null);
        List<String> tags = courseTagRepository.findByCourseIdOrderByTagAsc(course.getId()).stream()
                .map(tag -> tag.getTag())
                .toList();
        long enrollmentCount = enrollmentRepository.countByCourseIdInAndStatus(
                List.of(course.getId()), EnrollmentStatus.ACTIVE);

        return new StudioCourseDetail(
                course.getId(),
                course.getInstructorId(),
                course.getTitle(),
                course.getSummary(),
                course.getDescription(),
                course.getStatus().name(),
                course.getLevel() == null ? null : course.getLevel().name(),
                course.getLanguage(),
                course.getCategoryId(),
                categoryName,
                course.getSkills(),
                course.getRequirements(),
                tags,
                course.getPrice(),
                course.effectivePrice(clock.instant()),
                course.getDiscountPrice(),
                course.getDiscountEndsAt(),
                course.getCurrency(),
                course.getRatingAvg(),
                course.getRatingCount(),
                enrollmentCount,
                lessons.size(),
                course.getSuspendedReason(),
                course.getVersion(),
                course.getCreatedAt(),
                course.getUpdatedAt(),
                moduleResponses);
    }

    @Transactional(readOnly = true)
    public PageResponse<StudioStudentResponse> students(CurrentUser user, long courseId,
                                                        int page, int size) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found."));
        authorize(course, user);

        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size));
        Page<Enrollment> enrollments = enrollmentRepository
                .findByCourseIdAndStatus(courseId, EnrollmentStatus.ACTIVE, pageable);
        boolean showEmail = user.role() == Role.TENANT_ADMIN;
        List<StudioStudentResponse> items = enrollments.getContent().stream()
                .map(enrollment -> new StudioStudentResponse(
                        enrollment.getId(),
                        enrollment.getUserId(),
                        nameOrFallback(enrollment),
                        showEmail ? enrollment.getUserEmail() : null,
                        enrollment.getEnrolledAt(),
                        enrollment.getSource().name(),
                        enrollment.getStatus().name()))
                .toList();
        return new PageResponse<>(items, pageable.getPageNumber(), pageable.getPageSize(),
                enrollments.getTotalElements(), enrollments.getTotalPages());
    }

    @Transactional(readOnly = true)
    public StudioStatsResponse stats(CurrentUser user) {
        Long instructorId = user.role() == Role.INSTRUCTOR ? user.userId() : null;
        List<Course> courses = courseRepository.findAll().stream()
                .filter(course -> instructorId == null
                        || course.getInstructorId().equals(instructorId))
                .toList();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (CourseStatus value : CourseStatus.values()) {
            byStatus.put(value.name(), 0L);
        }
        courses.forEach(course -> byStatus.merge(course.getStatus().name(), 1L, Long::sum));

        List<Long> courseIds = courses.stream().map(Course::getId).toList();
        long totalEnrollments = courseIds.isEmpty() ? 0
                : enrollmentRepository.countByCourseIdInAndStatus(courseIds, EnrollmentStatus.ACTIVE);
        Instant since = clock.instant().minus(30, ChronoUnit.DAYS);
        long recent = courseIds.isEmpty() ? 0
                : enrollmentRepository.countByCourseIdInAndStatusAndEnrolledAtGreaterThanEqual(
                        courseIds, EnrollmentStatus.ACTIVE, since);

        BigDecimal ratingSum = courses.stream()
                .map(course -> course.getRatingAvg().multiply(
                        BigDecimal.valueOf(course.getRatingCount())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int ratingCount = courses.stream().mapToInt(Course::getRatingCount).sum();
        BigDecimal ratingAvg = ratingCount == 0 ? BigDecimal.ZERO
                : ratingSum.divide(BigDecimal.valueOf(ratingCount), 2, RoundingMode.HALF_UP);

        return new StudioStatsResponse(courses.size(), byStatus, totalEnrollments, recent,
                ratingAvg, ratingCount);
    }

    @Transactional(readOnly = true)
    public PageResponse<StudioReviewResponse> reviews(CurrentUser user, long courseId,
                                                      int page, int size) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found."));
        authorize(course, user);
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size));
        Page<Review> reviews = reviewRepository.findByCourseId(courseId, pageable);
        List<StudioReviewResponse> items = reviews.getContent().stream()
                .map(StudioReviewResponse::from)
                .toList();
        return new PageResponse<>(items, pageable.getPageNumber(), pageable.getPageSize(),
                reviews.getTotalElements(), reviews.getTotalPages());
    }

    private StudioCourseSummary summary(Course course, String categoryName) {
        long enrollmentCount = enrollmentRepository.countByCourseIdInAndStatus(
                List.of(course.getId()), EnrollmentStatus.ACTIVE);
        long lessonCount = lessonRepository.countByCourseId(course.getId());
        return new StudioCourseSummary(
                course.getId(),
                course.getTitle(),
                course.getSummary(),
                course.getStatus().name(),
                course.getLevel() == null ? null : course.getLevel().name(),
                course.getLanguage(),
                course.getPrice(),
                course.effectivePrice(clock.instant()),
                course.getCurrency(),
                course.getDiscountPrice(),
                course.getDiscountEndsAt(),
                course.getCategoryId(),
                categoryName,
                course.getRatingAvg(),
                course.getRatingCount(),
                enrollmentCount,
                lessonCount,
                course.getVersion(),
                course.getUpdatedAt());
    }

    private static void authorize(Course course, CurrentUser user) {
        if (user.role() == Role.TENANT_ADMIN) {
            return;
        }
        if (user.role() == Role.INSTRUCTOR && course.getInstructorId() == user.userId()) {
            return;
        }
        // Not "forbidden": the studio must not confirm that another publisher's id exists.
        throw new NotFoundException("Course not found.");
    }

    private static String nameOrFallback(Enrollment enrollment) {
        return enrollment.getUserName() == null || enrollment.getUserName().isBlank()
                ? "Learner " + enrollment.getUserId() : enrollment.getUserName();
    }

    private static CourseStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return CourseStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(
                    "status must be one of DRAFT, PUBLISHED, ARCHIVED, SUSPENDED.");
        }
    }

    private static String normalizeSearch(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        return q.trim();
    }

    private static int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
