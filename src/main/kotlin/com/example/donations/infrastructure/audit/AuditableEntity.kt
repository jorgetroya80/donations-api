package com.example.donations.infrastructure.audit

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
open class AuditableEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    open var id: Long? = null,

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    open var createdBy: String? = null,

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    open var createdAt: Instant? = null,

    @LastModifiedBy
    @Column(name = "updated_by")
    open var updatedBy: String? = null,

    @LastModifiedDate
    @Column(name = "updated_at")
    open var updatedAt: Instant? = null,
)
