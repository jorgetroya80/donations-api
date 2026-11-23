package com.example.donations.infrastructure.audit

import org.springframework.data.jpa.repository.JpaRepository

interface AuditLogRepository : JpaRepository<AuditLogEntry, Long> {

    fun findByEntityTypeAndEntityIdOrderByIdAsc(
        entityType: AuditEntityType,
        entityId: Long,
    ): List<AuditLogEntry>
}
