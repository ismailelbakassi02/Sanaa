package com.sanaa.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Metadata of an uploaded avatar or logo.
 *
 * <p>WHY the id is a random UUID and the path never contains the original name: the storage path is
 * not user-controlled, so a crafted filename cannot escape {@code IMAGES_DIR}. The bytes live on
 * disk; this row only records what they are.
 */
@Entity
@Table(name = "images")
public class Image {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(name = "path", nullable = false, length = 255)
    private String path;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Image() {
    }

    public Image(String contentType, String path, long sizeBytes) {
        this.id = UUID.randomUUID().toString();
        this.contentType = contentType;
        this.path = path;
        this.sizeBytes = sizeBytes;
    }

    public String getId() {
        return id;
    }

    public String getContentType() {
        return contentType;
    }

    public String getPath() {
        return path;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }
}
