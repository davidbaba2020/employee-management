package com.assessment.employee.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Abstract base for all JPA entities.
 *
 * <p>Centralises the fields that every table needs:
 * <ul>
 *   <li>{@code id}         — synthetic primary key (auto-generated)</li>
 *   <li>{@code createdAt}  — timestamp of first INSERT (immutable)</li>
 *   <li>{@code updatedAt}  — timestamp refreshed on every UPDATE</li>
 *   <li>{@code createdBy}  — principal that created the record (immutable)</li>
 *   <li>{@code updatedBy}  — principal that last modified the record</li>
 *   <li>{@code version}    — optimistic-locking counter</li>
 * </ul>
 *
 * <p>Auditing fields are populated automatically by
 * {@link AuditingEntityListener} — no {@code @PrePersist}/{@code @PreUpdate}
 * callbacks are needed in concrete entities.
 *
 * <p>When Spring Security is added, replace the {@code AuditConfig} bean with
 * one that reads the authenticated principal; all entities will pick it up
 * without any code changes here.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /**
     * Optimistic-locking version counter.
     * JPA increments this on every UPDATE; concurrent writes with a stale version
     * cause an {@code OptimisticLockException} rather than a silent overwrite.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
