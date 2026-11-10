package com.sanaa.payment.client;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.sanaa.payment.error.NotFoundException;
import com.sanaa.payment.error.ServiceUnavailableException;

/**
 * Reads the authoritative course data and the student's enrollments from course-service.
 *
 * <p>WHY forward the caller's token instead of a service token: course-service already enforces
 * tenant isolation and visibility with the same JWT, so the payment stores exactly the course the
 * student is allowed to see. A course-service 404 is a 404 here; any other failure is a 503 and the
 * payment is never created with guessed values.
 */
@Component
public class CourseServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CourseServiceClient.class);
    private static final String PUBLISHED = "PUBLISHED";
    private static final String ACTIVE = "ACTIVE";
    private static final String UNAVAILABLE = "The course catalog is temporarily unavailable.";

    private final RestClient restClient;

    public CourseServiceClient(RestClient courseRestClient) {
        this.restClient = courseRestClient;
    }

    /**
     * Returns the course only when it exists, is visible to the caller and is published.
     */
    public CourseSnapshot fetchPublishedCourse(long courseId, String authorization) {
        CourseSnapshot course;
        try {
            course = restClient.get()
                    .uri("/api/courses/{id}", courseId)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (request, response) -> {
                        throw new NotFoundException("Course not found.");
                    })
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new ServiceUnavailableException(UNAVAILABLE);
                    })
                    .body(CourseSnapshot.class);
        } catch (RestClientException exception) {
            log.warn("Course lookup failed for course {}: {}", courseId, exception.getMessage());
            throw new ServiceUnavailableException(UNAVAILABLE);
        }
        if (course == null) {
            throw new ServiceUnavailableException(UNAVAILABLE);
        }
        if (!PUBLISHED.equals(course.status())) {
            // A draft or archived course must look absent, exactly like course-service reports it.
            throw new NotFoundException("Course not found.");
        }
        return course;
    }

    public boolean hasActiveEnrollment(long courseId, String authorization) {
        EnrollmentSnapshot[] enrollments;
        try {
            enrollments = restClient.get()
                    .uri("/api/enrollments/me")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new ServiceUnavailableException(UNAVAILABLE);
                    })
                    .body(EnrollmentSnapshot[].class);
        } catch (RestClientException exception) {
            log.warn("Enrollment lookup failed for course {}: {}", courseId, exception.getMessage());
            throw new ServiceUnavailableException(UNAVAILABLE);
        }
        if (enrollments == null) {
            return false;
        }
        return Arrays.stream(enrollments).anyMatch(enrollment ->
                enrollment.course() != null
                        && enrollment.course().id() != null
                        && enrollment.course().id() == courseId
                        && ACTIVE.equals(enrollment.status()));
    }
}
