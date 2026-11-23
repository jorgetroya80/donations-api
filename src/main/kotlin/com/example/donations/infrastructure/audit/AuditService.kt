package com.example.donations.infrastructure.audit

import com.example.donations.user.UserRepository
import org.springframework.data.domain.AuditorAware
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Appends one row per write to audit_log. Callers are already inside the write's
 * transaction, so the audit row commits or rolls back with the change itself;
 * MANDATORY makes a call site without a transaction fail loudly instead of
 * committing the audit row on its own. Stores identifiers only — never field
 * values (ADR-005, ADR-006). The user id is kept beside the username because
 * usernames can be renamed and reused; it is null for "system".
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
class AuditService(
    private val auditLogRepository: AuditLogRepository,
    private val auditorAware: AuditorAware<String>,
    private val userRepository: UserRepository,
) {
    fun record(entityType: AuditEntityType, entityId: Long, operation: AuditOperation) {
        val username = auditorAware.currentAuditor.orElseThrow()
        auditLogRepository.save(
            AuditLogEntry(
                entityType = entityType,
                entityId = entityId,
                operation = operation,
                performedBy = username,
                performedById = userRepository.findIdByUsername(username),
                performedAt = Instant.now(),
            ),
        )
    }
}
