package com.example.donations.infrastructure.audit

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class AuditEntityType { DONATION, EXPENSE, DONOR, USER }

enum class AuditOperation { CREATE, UPDATE }

@Entity
@Table(name = "audit_log")
class AuditLogEntry(

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, updatable = false, length = 20)
    var entityType: AuditEntityType,

    @Column(name = "entity_id", nullable = false, updatable = false)
    var entityId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, updatable = false, length = 10)
    var operation: AuditOperation,

    @Column(name = "performed_by", nullable = false, updatable = false, length = 100)
    var performedBy: String,

    @Column(name = "performed_by_id", updatable = false)
    var performedById: Long?,

    @Column(name = "performed_at", nullable = false, updatable = false)
    var performedAt: Instant,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,
)
