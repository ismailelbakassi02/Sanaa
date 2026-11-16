package com.sanaa.course.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.sanaa.common.tenant.TenantContext;
import com.sanaa.course.crosstenant.CatalogueReadRepository;
import com.sanaa.course.crosstenant.CourseAccessRow;
import com.sanaa.course.crosstenant.CurriculumLessonRow;
import com.sanaa.course.crosstenant.EnrollmentRow;
import com.sanaa.course.crosstenant.MyReviewRow;
import com.sanaa.course.crosstenant.PublicCourseRow;
import com.sanaa.course.error.ConflictException;
import com.sanaa.course.error.ForbiddenException;
import com.sanaa.course.error.NotFoundException;
import com.sanaa.course.security.CurrentUser;
import com.sanaa.course.web.dto.learner.LearnerEnrollmentResponse;
import com.sanaa.course.web.dto.learner.LearnerHomeResponse;
import com.sanaa.course.web.dto.learner.LearnerTeacherResponse;
import com.sanaa.course.web.dto.learner.MyReviewResponse;
import com.sanaa.course.web.dto.learner.PurchaseInfoResponse;
import com.sanaa.course.web.dto.learner.ReviewRequest;
import com.sanaa.course.web.dto.publicapi.CategoryResponse;
import com.sanaa.course.web.dto.publicapi.PublicCourseCard;

/**
 * The learner read model and the facade of the learner cross-tenant writes.
 *
 * <p>Every read is filtered by the JWT subject, never by a request parameter. Every write resolves
 * the target tenant from a trusted course lookup and runs
 * {@code TenantContext.callAs(targetTenant, ...)} <em>before</em> the transaction opens.
 */
@Service
public class LearnerService {

    private static final int SUGGESTED_CATEGORIES = 4;
    private static final int TRENDING = 6;

    private final CatalogueReadRepository catalogue;
    private final LearnerWriteService learnerWriteService;
    private final PublicCatalogueService publicCatalogueService;
    private final Clock clock;

    public LearnerService(CatalogueReadRepository catalogue,
                          LearnerWriteService learnerWriteService,
                          PublicCatalogueService publicCatalogueService,
                          Clock clock) {
        this.catalogue = catalogue;
        this.learnerWriteService = learnerWriteService;
        this.publicCatalogueService = publicCatalogueService;
        this.clock = clock;
    }

    public List<LearnerEnrollmentResponse> myEnrollments(CurrentUser user) {
        Instant now = clock.instant();
        List<EnrollmentRow> enrollments = catalogue.activeEnrollments(user.userId());
        Map<Long, PublicCourseCard> cards = cards(
                enrollments.stream().map(EnrollmentRow::courseId).toList(), now);
        return enrollments.stream()
                .filter(enrollment -> cards.containsKey(enrollment.courseId()))
                .map(enrollment -> new LearnerEnrollmentResponse(
                        enrollment.enrollmentId(),
                        enrollment.enrolledAt(),
                        enrollment.source(),
                        cards.get(enrollment.courseId())))
                .toList();
    }

    public LearnerHomeResponse home(CurrentUser user) {
        List<LearnerEnrollmentResponse> myCourses = myEnrollments(user);
        List<LearnerTeacherResponse> teachers = catalogue.teachers(user.userId()).stream()
                .map(row -> new LearnerTeacherResponse(row.instructorId(), row.instructorName(),
                        row.publisherName(), row.publisherSlug()))
                .toList();
        List<CategoryResponse> categories = publicCatalogueService.categories();
        return new LearnerHomeResponse(
                myCourses,
                teachers,
                suggestCategories(user.userId(), categories),
                publicCatalogueService.trending(TRENDING),
                categories);
    }

    public MyReviewResponse myReview(CurrentUser user, long courseId) {
        MyReviewRow row = catalogue.myReview(user.userId(), courseId)
                .orElseThrow(() -> new NotFoundException("You have not reviewed this course."));
        return new MyReviewResponse(
                row.id(),
                row.courseId(),
                row.rating(),
                row.comment(),
                row.status(),
                row.createdAt(),
                row.updatedAt(),
                row.publisherReply(),
                row.repliedAt());
    }

    public PurchaseInfoResponse purchaseInfo(CurrentUser user, long courseId) {
        Instant now = clock.instant();
        CourseAccessRow course = purchasableCourse(courseId, now);
        return new PurchaseInfoResponse(
                course.id(),
                course.title(),
                course.price(),
                course.effectivePrice(now),
                course.currency(),
                course.discountActive(now),
                course.discountActive(now) ? course.discountEndsAt() : null,
                course.publisherName(),
                catalogue.hasActiveEnrollment(user.userId(), courseId),
                isOwnPublisher(course),
                course.effectivePrice(now).signum() == 0);
    }

    /**
     * Free enrollment. The tenant comes from the course row, not from the request. A concurrent
     * second request loses the unique-key race and reads the winner's enrollment instead of
     * reporting a 500.
     */
    public LearnerEnrollmentResponse enrollFree(CurrentUser user, long courseId) {
        Instant now = clock.instant();
        CourseAccessRow course = purchasableCourse(courseId, now);
        if (course.effectivePrice(now).signum() > 0) {
            throw new ConflictException("This course is not free. Create a payment instead.");
        }
        requireNotOwnPublisher(course);

        LearnerWriteService.EnrollmentRecord record;
        try {
            record = TenantContext.callAs(course.tenantId(), () -> learnerWriteService.enroll(
                    user.userId(), user.name(), user.email(), courseId, now));
        } catch (DataIntegrityViolationException race) {
            // Another request created the same row first. Its transaction has committed, so the
            // read model now returns it; anything else is a real failure and is rethrown.
            record = catalogue.activeEnrollment(user.userId(), courseId)
                    .map(row -> new LearnerWriteService.EnrollmentRecord(
                            row.enrollmentId(), row.enrolledAt(), row.source()))
                    .orElseThrow(() -> race);
        }

        PublicCourseCard card = cards(List.of(courseId), now).get(courseId);
        if (card == null) {
            throw new NotFoundException("Course not found.");
        }
        return new LearnerEnrollmentResponse(record.id(), record.enrolledAt(), record.source(),
                card);
    }

    public MyReviewResponse upsertReview(CurrentUser user, long courseId, ReviewRequest request) {
        Instant now = clock.instant();
        CourseAccessRow course = purchasableCourse(courseId, now);
        requireNotOwnPublisher(course);
        String comment = request.comment() == null || request.comment().isBlank()
                ? null : request.comment().trim();
        return TenantContext.callAs(course.tenantId(), () -> learnerWriteService.upsertReview(
                user.userId(), user.name(), courseId, request.rating(), comment));
    }

    private CourseAccessRow purchasableCourse(long courseId, Instant now) {
        return catalogue.findPurchasableCourse(courseId, now)
                .orElseThrow(() -> new NotFoundException("Course not found."));
    }

    private static boolean isOwnPublisher(CourseAccessRow course) {
        String tenant = TenantContext.get();
        return tenant != null && tenant.equals(course.tenantId());
    }

    private static void requireNotOwnPublisher(CourseAccessRow course) {
        if (isOwnPublisher(course)) {
            throw new ForbiddenException(
                    "You cannot enroll in or review a course of your own publisher.");
        }
    }

    private Map<Long, PublicCourseCard> cards(List<Long> courseIds, Instant now) {
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinct = courseIds.stream().distinct().toList();
        List<PublicCourseRow> rows = catalogue.findCoursesByIds(distinct, now,
                now.minus(30, ChronoUnit.DAYS), now.minus(7, ChronoUnit.DAYS));
        Map<Long, List<String>> tags = catalogue.tagsFor(distinct);
        Map<Long, PublicCourseCard> cards = new LinkedHashMap<>();
        for (PublicCourseRow row : rows) {
            cards.put(row.id(), new PublicCourseCard(
                    row.id(),
                    row.title(),
                    row.summary(),
                    row.level(),
                    row.language(),
                    row.price(),
                    row.effectivePrice(now),
                    row.currency(),
                    row.discountActive(now),
                    row.ratingAvg(),
                    row.ratingCount(),
                    row.studentCount(),
                    row.instructorName(),
                    row.publisherName(),
                    row.publisherSlug(),
                    row.publisherType(),
                    row.categorySlug(),
                    row.categoryName(),
                    tags.getOrDefault(row.id(), List.of())));
        }
        return cards;
    }

    private List<CategoryResponse> suggestCategories(long userId, List<CategoryResponse> categories) {
        Map<Long, CategoryResponse> byId = categories.stream()
                .collect(Collectors.toMap(CategoryResponse::id, Function.identity(),
                        (first, second) -> first, LinkedHashMap::new));
        List<CategoryResponse> suggested = new ArrayList<>();
        for (Long categoryId : catalogue.enrolledCategoryIds(userId)) {
            CategoryResponse category = byId.get(categoryId);
            if (category != null && suggested.stream()
                    .noneMatch(existing -> existing.id() == category.id())) {
                suggested.add(category);
            }
            if (suggested.size() == SUGGESTED_CATEGORIES) {
                return suggested;
            }
        }
        // Complete with the most populated categories, so a new learner still sees a useful grid.
        List<CategoryResponse> popular = categories.stream()
                .sorted((first, second) -> Long.compare(second.courseCount(), first.courseCount()))
                .toList();
        for (CategoryResponse category : popular) {
            if (suggested.size() == SUGGESTED_CATEGORIES) {
                break;
            }
            if (suggested.stream().noneMatch(existing -> existing.id() == category.id())) {
                suggested.add(category);
            }
        }
        return suggested;
    }
}
