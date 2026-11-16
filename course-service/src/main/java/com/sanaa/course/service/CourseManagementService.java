package com.sanaa.course.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.common.tenant.TenantContext;
import com.sanaa.course.domain.Category;
import com.sanaa.course.domain.Course;
import com.sanaa.course.domain.CourseLevel;
import com.sanaa.course.domain.CourseStatus;
import com.sanaa.course.domain.CourseTag;
import com.sanaa.course.domain.EnrollmentStatus;
import com.sanaa.course.domain.MediaStatus;
import com.sanaa.course.domain.Review;
import com.sanaa.course.error.BadRequestException;
import com.sanaa.course.error.ConflictException;
import com.sanaa.course.error.ForbiddenException;
import com.sanaa.course.error.NotFoundException;
import com.sanaa.course.error.UnprocessableEntityException;
import com.sanaa.course.event.PlaceholderSnapshotWriter;
import com.sanaa.course.repository.CategoryRepository;
import com.sanaa.course.repository.CourseRepository;
import com.sanaa.course.repository.CourseTagRepository;
import com.sanaa.course.repository.EnrollmentRepository;
import com.sanaa.course.repository.LessonRepository;
import com.sanaa.course.repository.ModuleRepository;
import com.sanaa.course.repository.ReviewRepository;
import com.sanaa.course.security.CurrentUser;
import com.sanaa.course.web.dto.studio.CreateCourseRequest;
import com.sanaa.course.web.dto.studio.DiscountRequest;
import com.sanaa.course.web.dto.studio.ReplyRequest;
import com.sanaa.course.web.dto.studio.StudioCourseDetail;
import com.sanaa.course.web.dto.studio.UpdateCourseRequest;

/**
 * Writes of the publisher studio. Every method runs in the tenant of the JWT and loads the course
 * through the tenant-filtered repository, so another publisher's id is a 404, never a 403.
 */
@Service
public class CourseManagementService {

    private static final String DEFAULT_CURRENCY = "MAD";
    private static final int MAX_TAGS = 10;
    private static final Pattern TAG_PATTERN =
            Pattern.compile("^[\\p{L}\\p{N}]+(?:[\\p{L}\\p{N} -]*[\\p{L}\\p{N}])?$");

    private final CourseRepository courseRepository;
    private final CategoryRepository categoryRepository;
    private final CourseTagRepository courseTagRepository;
    private final ModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ReviewRepository reviewRepository;
    private final StudioQueryService studioQueryService;
    private final PlaceholderSnapshotWriter placeholderSnapshotWriter;
    private final Clock clock;
    private final boolean requireReadyVideo;

    public CourseManagementService(CourseRepository courseRepository,
                                   CategoryRepository categoryRepository,
                                   CourseTagRepository courseTagRepository,
                                   ModuleRepository moduleRepository,
                                   LessonRepository lessonRepository,
                                   EnrollmentRepository enrollmentRepository,
                                   ReviewRepository reviewRepository,
                                   StudioQueryService studioQueryService,
                                   PlaceholderSnapshotWriter placeholderSnapshotWriter,
                                   Clock clock,
                                   @Value("${sanaa.course.publish.require-ready-video:true}")
                                   boolean requireReadyVideo) {
        this.courseRepository = courseRepository;
        this.categoryRepository = categoryRepository;
        this.courseTagRepository = courseTagRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.reviewRepository = reviewRepository;
        this.studioQueryService = studioQueryService;
        this.placeholderSnapshotWriter = placeholderSnapshotWriter;
        this.clock = clock;
        this.requireReadyVideo = requireReadyVideo;
    }

    @Transactional
    public StudioCourseDetail create(CurrentUser user, CreateCourseRequest request) {
        Long categoryId = validCategory(request.categoryId());
        Course course = new Course(
                request.title(),
                normalizeText(request.summary()),
                normalizeText(request.description()),
                parseLevel(request.level()),
                normalizeLanguage(request.language()),
                normalizePrice(request.price()),
                normalizeCurrency(request.currency()),
                user.userId());
        course.updateCatalogue(
                categoryId,
                request.title(),
                normalizeText(request.summary()),
                normalizeText(request.description()),
                parseLevel(request.level()),
                normalizeLanguage(request.language()),
                normalizeList(request.skills(), 12),
                normalizeList(request.requirements(), 12),
                normalizePrice(request.price()),
                normalizeCurrency(request.currency()));
        courseRepository.saveAndFlush(course);
        replaceTags(course.getId(), request.tags());

        // A studio can create its first course before the publisher event has been consumed. The
        // placeholder rows keep the public page joinable; the consumer overwrites them because its
        // occurredAt is after EPOCH.
        String tenantId = TenantContext.get();
        placeholderSnapshotWriter.ensurePublisher(tenantId, user.tenantSlug(), user.tenantType());
        placeholderSnapshotWriter.ensureInstructor(user.userId(), tenantId, user.name());

        return studioQueryService.buildDetail(course);
    }

    @Transactional
    public StudioCourseDetail update(CurrentUser user, long courseId, UpdateCourseRequest request) {
        Course course = loadForWrite(courseId, user);
        requireEditable(course);
        if (course.getVersion() != request.version()) {
            throw new ConflictException(
                    "The course was modified by another request. Reload it and retry.");
        }
        Long categoryId = validCategory(request.categoryId());
        course.updateCatalogue(
                categoryId,
                request.title(),
                normalizeText(request.summary()),
                normalizeText(request.description()),
                parseLevel(request.level()),
                normalizeLanguage(request.language()),
                normalizeList(request.skills(), 12),
                normalizeList(request.requirements(), 12),
                normalizePrice(request.price()),
                normalizeCurrency(request.currency()));
        replaceTags(courseId, request.tags());
        courseRepository.saveAndFlush(course);
        return studioQueryService.buildDetail(course);
    }

    /** A course with any enrollment is archived, never deleted: students paid for it. */
    @Transactional
    public void delete(CurrentUser user, long courseId) {
        Course course = loadForWrite(courseId, user);
        if (course.getStatus() == CourseStatus.SUSPENDED) {
            throw new ConflictException("A suspended course cannot be deleted.");
        }
        if (enrollmentRepository.countByCourseIdInAndStatus(List.of(courseId), EnrollmentStatus.ACTIVE) > 0) {
            throw new ConflictException(
                    "A course with enrollments cannot be deleted. Archive it instead.");
        }
        lessonRepository.deleteByCourseId(courseId);
        moduleRepository.deleteByCourseId(courseId);
        courseTagRepository.deleteAll(courseTagRepository.findByCourseIdOrderByTagAsc(courseId));
        courseRepository.delete(course);
    }

    @Transactional
    public StudioCourseDetail publish(CurrentUser user, long courseId) {
        Course course = loadForWrite(courseId, user);
        if (course.getStatus() == CourseStatus.ARCHIVED
                || course.getStatus() == CourseStatus.SUSPENDED) {
            throw new ConflictException("Archived or suspended courses cannot be published.");
        }
        if (course.getStatus() == CourseStatus.PUBLISHED) {
            return studioQueryService.buildDetail(course);
        }
        requirePublishable(course);
        course.publish();
        courseRepository.saveAndFlush(course);
        return studioQueryService.buildDetail(course);
    }

    @Transactional
    public StudioCourseDetail archive(CurrentUser user, long courseId) {
        Course course = loadForWrite(courseId, user);
        if (course.getStatus() == CourseStatus.SUSPENDED) {
            throw new ConflictException("A suspended course cannot be archived.");
        }
        if (course.getStatus() != CourseStatus.ARCHIVED) {
            course.archive();
            courseRepository.saveAndFlush(course);
        }
        return studioQueryService.buildDetail(course);
    }

    @Transactional
    public StudioCourseDetail updateDiscount(CurrentUser user, long courseId,
                                             DiscountRequest request) {
        Course course = loadForWrite(courseId, user);
        requireEditable(course);
        if (request.price().compareTo(course.getPrice()) >= 0) {
            throw new BadRequestException("A discount price must be below the list price.");
        }
        if (!request.endsAt().isAfter(clock.instant())) {
            throw new BadRequestException("A discount must end in the future.");
        }
        course.applyDiscount(request.price(), request.endsAt());
        courseRepository.saveAndFlush(course);
        return studioQueryService.buildDetail(course);
    }

    @Transactional
    public StudioCourseDetail clearDiscount(CurrentUser user, long courseId) {
        Course course = loadForWrite(courseId, user);
        requireEditable(course);
        course.clearDiscount();
        courseRepository.saveAndFlush(course);
        return studioQueryService.buildDetail(course);
    }

    /** A publisher may reply once per review. A second reply is a 409, not a silent overwrite. */
    @Transactional
    public void replyToReview(CurrentUser user, long reviewId, ReplyRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("Review not found."));
        Course course = loadForWrite(review.getCourseId(), user);
        if (review.getRepliedAt() != null) {
            throw new ConflictException("This review already has a reply.");
        }
        review.reply(request.reply().trim(), clock.instant());
        reviewRepository.saveAndFlush(review);
    }

    private void requirePublishable(Course course) {
        List<String> missing = new ArrayList<>();
        if (course.getCategoryId() == null) {
            missing.add("category");
        }
        if (course.getSummary() == null || course.getSummary().isBlank()) {
            missing.add("summary");
        }
        if (course.getSkills().isEmpty()) {
            missing.add("at least one skill");
        }
        if (requireReadyVideo
                && lessonRepository.countByCourseIdAndMediaStatus(course.getId(), MediaStatus.READY) == 0) {
            missing.add("at least one lesson with a ready video");
        }
        if (!missing.isEmpty()) {
            throw new UnprocessableEntityException(
                    "The course is not ready to be published: " + String.join(", ", missing) + ".");
        }
    }

    /**
     * Ownership rules shared by every studio write. A tenant administrator may edit any course of
     * the tenant; an instructor only their own. Another instructor's course is a 404 in the studio,
     * because the studio is a private workspace and its ids must not leak.
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

    private Long validCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BadRequestException("Unknown category."));
        return category.getId();
    }

    private void replaceTags(long courseId, List<String> requested) {
        List<String> tags = normalizeTags(requested);
        courseTagRepository.deleteAll(courseTagRepository.findByCourseIdOrderByTagAsc(courseId));
        // Flush the deletes before the inserts: Hibernate runs inserts first, and the unique key
        // (course_id, tag) would reject re-sending an unchanged tag.
        courseTagRepository.flush();
        if (!tags.isEmpty()) {
            List<CourseTag> rows = tags.stream().map(tag -> new CourseTag(courseId, tag)).toList();
            courseTagRepository.saveAll(rows);
        }
    }

    private static List<String> normalizeTags(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String raw : requested) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String tag = raw.trim().toLowerCase(Locale.ROOT);
            if (!TAG_PATTERN.matcher(tag).matches() || tag.length() > 30) {
                throw new BadRequestException("Invalid tag: " + tag + ".");
            }
            unique.add(tag);
        }
        if (unique.size() > MAX_TAGS) {
            throw new BadRequestException("A course can have at most " + MAX_TAGS + " tags.");
        }
        return List.copyOf(unique);
    }

    private static List<String> normalizeList(List<String> values, int maximum) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> cleaned = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
        if (cleaned.size() > maximum) {
            throw new BadRequestException("At most " + maximum + " values are allowed.");
        }
        return cleaned;
    }

    private static CourseLevel parseLevel(String level) {
        if (level == null || level.isBlank()) {
            return null;
        }
        try {
            return CourseLevel.valueOf(level.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("level must be one of BEGINNER, INTERMEDIATE, ADVANCED.");
        }
    }

    private static String normalizeLanguage(String language) {
        return language == null || language.isBlank() ? "fr" : language.trim().toLowerCase(Locale.ROOT);
    }

    private static BigDecimal normalizePrice(BigDecimal price) {
        return price == null ? BigDecimal.ZERO : price;
    }

    private static String normalizeCurrency(String currency) {
        return currency == null || currency.isBlank()
                ? DEFAULT_CURRENCY : currency.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeText(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
