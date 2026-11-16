package com.sanaa.course.web.dto.learner;

/**
 * My dashboard: my courses, the teachers and publishers I already learn from, the categories of my
 * enrollments (completed with the most populated ones) and the current trending courses.
 */
public record LearnerHomeResponse(
        java.util.List<LearnerEnrollmentResponse> myCourses,
        java.util.List<LearnerTeacherResponse> myTeachers,
        java.util.List<com.sanaa.course.web.dto.publicapi.CategoryResponse> suggestedCategories,
        java.util.List<com.sanaa.course.web.dto.publicapi.PublicCourseCard> trending,
        java.util.List<com.sanaa.course.web.dto.publicapi.CategoryResponse> categories) {
}
