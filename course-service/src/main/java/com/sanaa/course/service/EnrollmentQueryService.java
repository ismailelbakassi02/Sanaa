package com.sanaa.course.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.course.domain.Course;
import com.sanaa.course.domain.Enrollment;
import com.sanaa.course.domain.EnrollmentStatus;
import com.sanaa.course.repository.CourseRepository;
import com.sanaa.course.repository.EnrollmentRepository;
import com.sanaa.course.security.CurrentUser;
import com.sanaa.course.web.dto.CourseSummary;
import com.sanaa.course.web.dto.MyEnrollmentResponse;

@Service
public class EnrollmentQueryService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;

    public EnrollmentQueryService(EnrollmentRepository enrollmentRepository,
                                  CourseRepository courseRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional(readOnly = true)
    public List<MyEnrollmentResponse> myEnrollments(CurrentUser user) {
        List<Enrollment> enrollments = enrollmentRepository
                .findByUserIdAndStatusOrderByEnrolledAtDesc(user.userId(), EnrollmentStatus.ACTIVE);
        if (enrollments.isEmpty()) {
            return List.of();
        }

        List<Long> courseIds = enrollments.stream()
                .map(Enrollment::getCourseId)
                .distinct()
                .toList();
        Map<Long, Course> courses = courseRepository.findAllById(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));

        return enrollments.stream()
                .filter(enrollment -> courses.containsKey(enrollment.getCourseId()))
                .map(enrollment -> new MyEnrollmentResponse(
                        enrollment.getId(),
                        enrollment.getStatus().name(),
                        enrollment.getEnrolledAt(),
                        CourseSummary.from(courses.get(enrollment.getCourseId()))))
                .toList();
    }
}
