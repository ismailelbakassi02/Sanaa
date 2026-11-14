package com.sanaa.auth.web.dto;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract guard for the anonymous pages.
 *
 * <p>WHY reflection and not only HTTP assertions: an HTTP test can only notice a field that a
 * particular fixture happens to populate. Adding {@code email} or an internal id to a public record
 * would leak it on the next request even if every existing test still passes. This test fails the
 * build as soon as the field list changes, so the change must be reviewed on purpose.
 */
class PublicDtoContractTest {

    @Test
    void publicPublisherPageExposesExactlyTheAllowedFields() {
        assertComponents(PublicPublisherResponse.class,
                "slug", "name", "type", "headline", "description",
                "websiteUrl", "instagramUrl", "linkedinUrl", "youtubeUrl",
                "logoImageId", "primaryColor", "team");
    }

    @Test
    void publicInstructorPageExposesExactlyTheAllowedFields() {
        assertComponents(PublicInstructorResponse.class,
                "userId", "fullName", "headline", "bio",
                "websiteUrl", "instagramUrl", "linkedinUrl", "youtubeUrl",
                "avatarImageId", "publisherSlug", "publisherName");
    }

    @Test
    void publicTeamMemberExposesExactlyTheAllowedFields() {
        assertComponents(PublicPublisherResponse.PublicTeamMember.class,
                "userId", "fullName", "headline", "avatarImageId");
    }

    @Test
    void noPublicFieldLooksLikeContactOrCredentialData() {
        List<Class<?>> publicRecords = List.of(
                PublicPublisherResponse.class,
                PublicPublisherResponse.PublicTeamMember.class,
                PublicInstructorResponse.class);
        for (Class<?> record : publicRecords) {
            for (String name : componentNames(record)) {
                String lower = name.toLowerCase(Locale.ROOT);
                assertThat(lower)
                        .as("%s must not expose contact or credential data", record.getSimpleName())
                        .doesNotContain("email", "mail", "password", "phone", "contact", "secret",
                                "token", "hash");
            }
        }
    }

    private static void assertComponents(Class<?> record, String... allowed) {
        assertThat(record.isRecord())
                .as("%s must stay a record so its shape is explicit", record.getSimpleName())
                .isTrue();
        assertThat(componentNames(record))
                .as("%s public field list changed: review the privacy rule in architecture 5.3",
                        record.getSimpleName())
                .containsExactlyElementsOf(List.of(allowed));
    }

    private static List<String> componentNames(Class<?> record) {
        return Arrays.stream(record.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }
}
