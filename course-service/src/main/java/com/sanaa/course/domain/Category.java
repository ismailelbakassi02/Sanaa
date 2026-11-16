package com.sanaa.course.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Global reference data seeded by Flyway V2. Not tenant-owned: every publisher shares the same
 * twelve categories, exactly like the public catalogue does.
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slug", nullable = false, length = 64)
    private String slug;

    @Column(name = "name_fr", nullable = false, length = 80)
    private String nameFr;

    protected Category() {
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getNameFr() {
        return nameFr;
    }
}
