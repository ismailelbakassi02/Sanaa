package com.sanaa.course.web.dto.publicapi;

/** A category with the number of published courses it currently contains. */
public record CategoryResponse(long id, String slug, String name, long courseCount) {
}
