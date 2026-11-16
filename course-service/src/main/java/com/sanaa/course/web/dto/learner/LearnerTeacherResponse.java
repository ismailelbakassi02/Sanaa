package com.sanaa.course.web.dto.learner;

/** A distinct instructor (and their publisher) the learner already has a course from. */
public record LearnerTeacherResponse(
        long instructorId,
        String instructorName,
        String publisherName,
        String publisherSlug) {
}
