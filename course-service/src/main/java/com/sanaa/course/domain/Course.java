package com.sanaa.course.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * A tenant-owned course. Hibernate fills {@code tenantId} from {@code TenantContext} and filters
 * every read by it, so no repository method accepts a tenant id.
 *
 * <p>{@code @Version} protects concurrent edits: every update must present the version the client
 * last read. {@code instructorId} is the JWT subject of the creator and is never taken from input.
 *
 * <p>{@code skills} and {@code requirements} are stored as MySQL JSON columns. Hibernate 7 maps a
 * {@code List<String>} through its Jackson 3 format mapper, so the API works with typed lists and
 * the database still uses the JSON type required by the catalogue schema.
 */
@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 36)
    private String tenantId;

    @Column(name = "instructor_id", nullable = false)
    private Long instructorId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "summary", length = 200)
    private String summary;

    @Column(name = "description", length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", length = 16)
    private CourseLevel level;

    @Column(name = "language", nullable = false, length = 2)
    private String language;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skills")
    private List<String> skills;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requirements")
    private List<String> requirements;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "discount_price", precision = 10, scale = 2)
    private BigDecimal discountPrice;

    @Column(name = "discount_ends_at")
    private Instant discountEndsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CourseStatus status;

    @Column(name = "suspended_reason", length = 500)
    private String suspendedReason;

    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
    private BigDecimal ratingAvg;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    /**
     * ACTIVE enrollments, denormalised by {@code CourseStatisticsService} so a catalogue read never
     * counts the enrollments table. Derived data: recompute it, never increment it.
     */
    @Column(name = "student_count", nullable = false)
    private long studentCount;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Course() {
    }

    public Course(String title, String summary, String description, CourseLevel level,
                  String language, BigDecimal price, String currency, Long instructorId) {
        this.title = title;
        this.summary = summary;
        this.description = description;
        this.level = level;
        this.language = language;
        this.price = price;
        this.currency = currency;
        this.instructorId = instructorId;
        this.status = CourseStatus.DRAFT;
        this.skills = List.of();
        this.requirements = List.of();
        this.ratingAvg = BigDecimal.ZERO;
        this.ratingCount = 0;
        this.studentCount = 0;
    }

    public void updateCatalogue(Long categoryId, String title, String summary, String description,
                                CourseLevel level, String language, List<String> skills,
                                List<String> requirements, BigDecimal price, String currency) {
        this.categoryId = categoryId;
        this.title = title;
        this.summary = summary;
        this.description = description;
        this.level = level;
        this.language = language;
        this.skills = skills;
        this.requirements = requirements;
        this.price = price;
        this.currency = currency;
    }

    /**
     * The price charged today. The discount applies only while {@code now} is strictly before its
     * end; at the exact end instant the full price is back.
     */
    public BigDecimal effectivePrice(Instant now) {
        if (discountPrice != null && discountEndsAt != null && now.isBefore(discountEndsAt)) {
            return discountPrice;
        }
        return price;
    }

    public void applyDiscount(BigDecimal discountPrice, Instant discountEndsAt) {
        this.discountPrice = discountPrice;
        this.discountEndsAt = discountEndsAt;
    }

    public void clearDiscount() {
        this.discountPrice = null;
        this.discountEndsAt = null;
    }

    public void updateRating(BigDecimal ratingAvg, int ratingCount) {
        this.ratingAvg = ratingAvg;
        this.ratingCount = ratingCount;
    }

    public void updateStudentCount(long studentCount) {
        this.studentCount = studentCount;
    }

    public void publish() {
        this.status = CourseStatus.PUBLISHED;
    }

    public void archive() {
        this.status = CourseStatus.ARCHIVED;
    }

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Long getInstructorId() {
        return instructorId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getDescription() {
        return description;
    }

    public CourseLevel getLevel() {
        return level;
    }

    public String getLanguage() {
        return language;
    }

    public List<String> getSkills() {
        return skills == null ? List.of() : skills;
    }

    public List<String> getRequirements() {
        return requirements == null ? List.of() : requirements;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getDiscountPrice() {
        return discountPrice;
    }

    public Instant getDiscountEndsAt() {
        return discountEndsAt;
    }

    public CourseStatus getStatus() {
        return status;
    }

    public String getSuspendedReason() {
        return suspendedReason;
    }

    public BigDecimal getRatingAvg() {
        return ratingAvg;
    }

    public int getRatingCount() {
        return ratingCount;
    }

    public long getStudentCount() {
        return studentCount;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
