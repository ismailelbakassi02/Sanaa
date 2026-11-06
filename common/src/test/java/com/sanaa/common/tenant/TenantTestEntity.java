package com.sanaa.common.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;

/**
 * Test-only entity used to prove Hibernate's {@code @TenantId} filtering. It is never packaged
 * into the main jar and no production code may depend on it.
 */
@Entity
@Table(name = "tenant_test_entities")
public class TenantTestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    protected TenantTestEntity() {
    }

    public TenantTestEntity(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }
}
