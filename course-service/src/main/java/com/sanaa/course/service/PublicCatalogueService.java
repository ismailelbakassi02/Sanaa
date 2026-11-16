package com.sanaa.course.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sanaa.common.event.EventJson;
import com.sanaa.course.crosstenant.CatalogueReadRepository;
import com.sanaa.course.crosstenant.CategoryCountRow;
import com.sanaa.course.crosstenant.CurriculumLessonRow;
import com.sanaa.course.crosstenant.CurriculumModuleRow;
import com.sanaa.course.crosstenant.PublicCoursePage;
import com.sanaa.course.crosstenant.PublicCourseRow;
import com.sanaa.course.crosstenant.PublicCourseSearch;
import com.sanaa.course.crosstenant.RatingAggregate;
import com.sanaa.course.crosstenant.ReviewRow;
import com.sanaa.course.domain.CourseLevel;
import com.sanaa.course.error.BadRequestException;
import com.sanaa.course.error.NotFoundException;
import com.sanaa.course.web.dto.PageResponse;
import com.sanaa.course.web.dto.publicapi.CategoryResponse;
import com.sanaa.course.web.dto.publicapi.PublicCourseCard;
import com.sanaa.course.web.dto.publicapi.PublicCourseDetail;
import com.sanaa.course.web.dto.publicapi.PublicLessonResponse;
import com.sanaa.course.web.dto.publicapi.PublicModuleResponse;
import com.sanaa.course.web.dto.publicapi.PublicReviewResponse;
import com.sanaa.course.web.dto.publicapi.PublicReviewsResponse;

/**
 * The anonymous public catalogue.
 *
 * <p>WHY no transaction: the detail endpoint increments a view. A transaction would make a failed
 * counter update roll back the page read. Every statement is independent, and the increment is
 * best-effort by design: a views failure must never block a course page.
 */
@Service
public class PublicCatalogueService {

    private static final Logger log = LoggerFactory.getLogger(PublicCatalogueService.class);

    private static final int MAX_PAGE_SIZE = 24;
    private static final Set<String> SORTS =
            Set.of("trending", "newest", "rating", "price_asc", "price_desc");

    private final CatalogueReadRepository catalogue;
    private final Clock clock;

    public PublicCatalogueService(CatalogueReadRepository catalogue, Clock clock) {
        this.catalogue = catalogue;
        this.clock = clock;
    }

    public List<CategoryResponse> categories() {
        return catalogue.categories().stream()
                .map(row -> new CategoryResponse(row.id(), row.slug(), row.name(),
                        row.courseCount()))
                .toList();
    }

    public PageResponse<PublicCourseCard> search(String q, String category, String tag, String level,
                                                 Boolean free, BigDecimal minPrice,
                                                 BigDecimal maxPrice, String sort, int page,
                                                 int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? MAX_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        String safeSort = sort == null || sort.isBlank()
                ? "trending" : sort.trim().toLowerCase(Locale.ROOT);
        if (!SORTS.contains(safeSort)) {
            throw new BadRequestException(
                    "sort must be one of trending, newest, rating, price_asc, price_desc.");
        }
        if (minPrice != null && minPrice.signum() < 0 || maxPrice != null && maxPrice.signum() < 0) {
            throw new BadRequestException("Prices cannot be negative.");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("minPrice cannot be greater than maxPrice.");
        }

        PublicCourseSearch search = new PublicCourseSearch(
                text(q),
                lower(category),
                lower(tag),
                parseLevel(level),
                Boolean.TRUE.equals(free),
                minPrice,
                maxPrice,
                safeSort,
                safePage,
                safeSize);
        Instant now = clock.instant();
        PublicCoursePage result = catalogue.search(search, now,
                now.minus(30, ChronoUnit.DAYS), now.minus(7, ChronoUnit.DAYS));

        Map<Long, List<String>> tags = catalogue.tagsFor(
                result.items().stream().map(PublicCourseRow::id).toList());
        List<PublicCourseCard> items = result.items().stream()
                .map(row -> card(row, tags.getOrDefault(row.id(), List.of()), now))
                .toList();
        int totalPages = result.totalElements() == 0 ? 0
                : (int) Math.ceil((double) result.totalElements() / safeSize);
        return new PageResponse<>(items, safePage, safeSize, result.totalElements(), totalPages);
    }

    public List<PublicCourseCard> trending(int limit) {
        int safeLimit = limit <= 0 ? 8 : Math.min(limit, 24);
        Instant now = clock.instant();
        List<PublicCourseRow> rows = catalogue.trending(now,
                now.minus(30, ChronoUnit.DAYS), now.minus(7, ChronoUnit.DAYS), safeLimit);
        Map<Long, List<String>> tags = catalogue.tagsFor(
                rows.stream().map(PublicCourseRow::id).toList());
        return rows.stream()
                .map(row -> card(row, tags.getOrDefault(row.id(), List.of()), now))
                .toList();
    }

    public PublicCourseDetail detail(long courseId) {
        Instant now = clock.instant();
        PublicCourseRow row = catalogue.findPublishedCourse(courseId, now,
                        now.minus(30, ChronoUnit.DAYS), now.minus(7, ChronoUnit.DAYS))
                .orElseThrow(() -> new NotFoundException("Course not found."));

        // Counter first: a failure is logged and ignored, never turned into a 500.
        try {
            catalogue.incrementView(courseId, java.time.LocalDate.now(clock));
        } catch (RuntimeException exception) {
            log.debug("Could not increment the view counter of course {}: {}",
                    courseId, exception.toString());
        }

        List<PublicModuleResponse> modules = curriculum(courseId);
        List<String> tags = catalogue.tagsFor(List.of(courseId)).getOrDefault(courseId, List.of());
        return new PublicCourseDetail(
                row.id(),
                row.title(),
                row.summary(),
                row.description(),
                row.level(),
                row.language(),
                row.price(),
                row.effectivePrice(now),
                row.currency(),
                row.discountActive(now),
                row.discountActive(now) ? row.discountEndsAt() : null,
                row.ratingAvg(),
                row.ratingCount(),
                row.studentCount(),
                row.viewCount(),
                row.instructorId(),
                row.instructorName(),
                row.publisherName(),
                row.publisherSlug(),
                row.publisherType(),
                row.categorySlug(),
                row.categoryName(),
                jsonList(row.skillsJson()),
                jsonList(row.requirementsJson()),
                tags,
                modules);
    }

    public PublicReviewsResponse reviews(long courseId, int page, int size) {
        Instant now = clock.instant();
        // A draft course has no public reviews endpoint either: its id must behave like a 404.
        catalogue.findPublishedCourse(courseId, now, now.minus(30, ChronoUnit.DAYS),
                        now.minus(7, ChronoUnit.DAYS))
                .orElseThrow(() -> new NotFoundException("Course not found."));

        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : Math.min(size, MAX_PAGE_SIZE);
        List<ReviewRow> rows = catalogue.visibleReviews(courseId, safeSize, safePage * safeSize);
        long total = catalogue.countVisibleReviews(courseId);
        RatingAggregate aggregate = catalogue.ratingAggregate(courseId);
        List<PublicReviewResponse> items = rows.stream()
                .map(row -> new PublicReviewResponse(
                        row.id(),
                        maskName(row.userName()),
                        row.rating(),
                        row.comment(),
                        row.publisherReply(),
                        row.repliedAt(),
                        row.createdAt()))
                .toList();
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);
        return new PublicReviewsResponse(items, safePage, safeSize, total, totalPages,
                aggregate.average(), aggregate.count());
    }

    private List<PublicModuleResponse> curriculum(long courseId) {
        List<CurriculumLessonRow> lessons = catalogue.lessons(courseId);
        Map<Long, List<PublicLessonResponse>> byModule = new LinkedHashMap<>();
        for (CurriculumLessonRow lesson : lessons) {
            byModule.computeIfAbsent(lesson.moduleId(), key -> new java.util.ArrayList<>())
                    .add(new PublicLessonResponse(
                            lesson.id(),
                            lesson.title(),
                            lesson.description(),
                            lesson.position(),
                            lesson.durationSeconds(),
                            lesson.freePreview(),
                            lesson.available()));
        }
        return catalogue.modules(courseId).stream()
                .map((CurriculumModuleRow module) -> new PublicModuleResponse(
                        module.id(),
                        module.title(),
                        module.position(),
                        byModule.getOrDefault(module.id(), List.of())))
                .toList();
    }

    private static PublicCourseCard card(PublicCourseRow row, List<String> tags, Instant now) {
        return new PublicCourseCard(
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
                tags);
    }

    /** "Prenom Nom" becomes "Prenom N.". The public page never shows a learner's full name. */
    static String maskName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Learner";
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0];
        }
        return parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }

    private static List<String> jsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.of(EventJson.mapper().readValue(json, String[].class));
        } catch (RuntimeException exception) {
            // A malformed JSON value must not break the catalogue page.
            return List.of();
        }
    }

    private static String parseLevel(String level) {
        if (level == null || level.isBlank()) {
            return null;
        }
        try {
            return CourseLevel.valueOf(level.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("level must be one of BEGINNER, INTERMEDIATE, ADVANCED.");
        }
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String lower(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
