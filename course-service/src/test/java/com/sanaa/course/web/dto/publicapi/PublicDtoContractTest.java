package com.sanaa.course.web.dto.publicapi;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract guard for the anonymous pages.
 *
 * <p>WHY reflection and not only HTTP assertions: an HTTP test can only notice a field a particular
 * fixture happens to populate. Adding {@code email}, a tenant id or a media path to a public record
 * would leak it on the next request even if every existing test still passes. This test fails the
 * build as soon as the field list changes, so the change must be reviewed on purpose.
 */
class PublicDtoContractTest {

    private static final List<String> FORBIDDEN_FRAGMENTS =
            List.of("email", "tenant", "media", "payment", "storage", "hls", "password");

    @Test
    void courseCardExposesOnlyTheCatalogueFields() {
        assertComponents(PublicCourseCard.class,
                "id", "title", "summary", "level", "language", "price", "effectivePrice",
                "currency", "discountActive", "ratingAvg", "ratingCount", "studentCount",
                "instructorName", "publisherName", "publisherSlug", "publisherType",
                "categorySlug", "categoryName", "tags");
    }

    @Test
    void courseDetailExposesTheCurriculumButNoPrivateColumns() {
        assertComponents(PublicCourseDetail.class,
                "id", "title", "summary", "description", "level", "language", "price",
                "effectivePrice", "currency", "discountActive", "discountEndsAt", "ratingAvg",
                "ratingCount", "studentCount", "viewCount", "instructorId", "instructorName",
                "publisherName", "publisherSlug", "publisherType", "categorySlug", "categoryName",
                "skills", "requirements", "tags", "modules");
    }

    @Test
    void curriculumLinesNeverExposeAPlaybackPath() {
        assertComponents(PublicLessonResponse.class,
                "id", "title", "description", "position", "durationSeconds", "freePreview",
                "available");
        assertComponents(PublicModuleResponse.class, "id", "title", "position", "lessons");
    }

    @Test
    void reviewsExposeTheMaskedNameAndThePublisherReplyOnly() {
        assertComponents(PublicReviewResponse.class,
                "id", "learnerName", "rating", "comment", "publisherReply", "repliedAt",
                "createdAt");
        assertComponents(PublicReviewsResponse.class,
                "items", "page", "size", "totalElements", "totalPages", "ratingAvg", "ratingCount");
    }

    @Test
    void categoriesExposeTheirSlugAndCount() {
        assertComponents(CategoryResponse.class, "id", "slug", "name", "courseCount");
    }

    private static void assertComponents(Class<? extends Record> type, String... expected) {
        List<String> actual = Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertThat(actual).as(type.getSimpleName()).containsExactly(expected);
        assertThat(actual).as(type.getSimpleName())
                .noneMatch(name -> FORBIDDEN_FRAGMENTS.stream()
                        .anyMatch(fragment -> name.toLowerCase(Locale.ROOT).contains(fragment)));
    }
}
