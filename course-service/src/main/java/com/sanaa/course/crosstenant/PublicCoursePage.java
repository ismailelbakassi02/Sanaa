package com.sanaa.course.crosstenant;

import java.util.List;

public record PublicCoursePage(List<PublicCourseRow> items, long totalElements) {
}
