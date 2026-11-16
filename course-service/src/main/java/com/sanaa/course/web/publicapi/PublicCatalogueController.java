package com.sanaa.course.web.publicapi;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sanaa.course.service.PublicCatalogueService;
import com.sanaa.course.web.dto.PageResponse;
import com.sanaa.course.web.dto.publicapi.CategoryResponse;
import com.sanaa.course.web.dto.publicapi.PublicCourseCard;
import com.sanaa.course.web.dto.publicapi.PublicCourseDetail;
import com.sanaa.course.web.dto.publicapi.PublicReviewsResponse;

/**
 * Anonymous catalogue. Nothing here needs a token, and nothing here returns an email, a tenant id,
 * a media path or a payment id (architecture section 5.3).
 */
@RestController
@RequestMapping("/api/public")
public class PublicCatalogueController {

    private final PublicCatalogueService publicCatalogueService;

    public PublicCatalogueController(PublicCatalogueService publicCatalogueService) {
        this.publicCatalogueService = publicCatalogueService;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return publicCatalogueService.categories();
    }

    @GetMapping("/courses")
    public PageResponse<PublicCourseCard> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Boolean free,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "trending") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        return publicCatalogueService.search(q, category, tag, level, free, minPrice, maxPrice,
                sort, page, size);
    }

    @GetMapping("/courses/trending")
    public List<PublicCourseCard> trending(
            @RequestParam(defaultValue = "8") int limit) {
        return publicCatalogueService.trending(limit);
    }

    @GetMapping("/courses/{id}")
    public PublicCourseDetail detail(@PathVariable long id) {
        return publicCatalogueService.detail(id);
    }

    @GetMapping("/courses/{id}/reviews")
    public PublicReviewsResponse reviews(
            @PathVariable long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return publicCatalogueService.reviews(id, page, size);
    }
}
