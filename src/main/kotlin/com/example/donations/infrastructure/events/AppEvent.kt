package com.example.donations.infrastructure.events

import com.example.donations.expense.ExpenseCategory
import org.slf4j.event.Level
import java.math.BigDecimal

/**
 * The closed set of events this application emits, named with the OWASP
 * Application Logging Vocabulary (ADR-005). Each event declares its own fields,
 * so donor personal data has no field to travel in.
 */
sealed interface AppEvent {
    val name: String
    val level: Level
    val fields: Map<String, Any?>
}

data class LoginSucceeded(val userid: String, val sourceIp: String) : AppEvent {
    override val name = "authn_login_success"
    override val level = Level.INFO
    override val fields = mapOf("userid" to userid, "sourceIp" to sourceIp)
}

data class LoginFailed(val userid: String, val sourceIp: String, val locked: Boolean) : AppEvent {
    override val name = "authn_login_fail"
    override val level = Level.WARN
    override val fields = mapOf("userid" to userid, "sourceIp" to sourceIp, "locked" to locked)
}

data class AccountLocked(val userid: String, val reason: String, val maxlimit: Int) : AppEvent {
    override val name = "authn_login_lock"
    override val level = Level.WARN
    override val fields = mapOf("userid" to userid, "reason" to reason, "maxlimit" to maxlimit)
}

data class PasswordChanged(val userid: String) : AppEvent {
    override val name = "authn_password_change"
    override val level = Level.INFO
    override val fields = mapOf("userid" to userid)
}

// OWASP marks this CRITICAL; SLF4J has no such level, so it maps to ERROR (ADR-005).
data class PasswordChangeFailed(val userid: String) : AppEvent {
    override val name = "authn_password_change_fail"
    override val level = Level.ERROR
    override val fields = mapOf("userid" to userid)
}

// OWASP marks this CRITICAL; SLF4J has no such level, so it maps to ERROR (ADR-005).
data class AuthorizationFailed(val userid: String, val resource: String) : AppEvent {
    override val name = "authz_fail"
    override val level = Level.ERROR
    override val fields = mapOf("userid" to userid, "resource" to resource)
}

data class AdminAction(val userid: String, val action: String, val targetId: Long) : AppEvent {
    override val name = "authz_admin"
    override val level = Level.WARN
    override val fields = mapOf("userid" to userid, "action" to action, "targetId" to targetId)

    /**
     * A closed vocabulary: this is the only free-text field in the event set, so
     * request data must never be interpolated into it (ADR-005). OWASP calls the
     * field "event"; it is keyed "action" here because EventLogger already writes
     * the event name under "event".
     */
    companion object {
        const val USER_CREATE = "user_create"
        const val USER_UPDATE = "user_update"
        const val PASSWORD_RESET = "user_password_reset"
    }
}

data class AuthorizationChanged(val userid: String, val from: String, val to: String) : AppEvent {
    override val name = "authz_change"
    override val level = Level.WARN
    override val fields = mapOf("userid" to userid, "from" to from, "to" to to)
}

// donorId is null for anonymous donations, which have no donor to identify.
data class DonationCreated(val donationId: Long, val donorId: Long?, val amount: BigDecimal) : AppEvent {
    override val name = "donation_create"
    override val level = Level.INFO
    override val fields = mapOf("donationId" to donationId, "donorId" to donorId, "amount" to amount)
}

data class DonationUpdated(val donationId: Long) : AppEvent {
    override val name = "donation_update"
    override val level = Level.INFO
    override val fields = mapOf("donationId" to donationId)
}

// The donor aggregate holds names, national ids, addresses and emails. These two
// events declare an id and nothing else, so none of it is representable.
data class DonorCreated(val donorId: Long) : AppEvent {
    override val name = "donor_create"
    override val level = Level.INFO
    override val fields = mapOf("donorId" to donorId)
}

data class DonorUpdated(val donorId: Long) : AppEvent {
    override val name = "donor_update"
    override val level = Level.INFO
    override val fields = mapOf("donorId" to donorId)
}

// category is the enum, not a String, so no free text can reach the field.
data class ExpenseCreated(
    val expenseId: Long,
    val amount: BigDecimal,
    val category: ExpenseCategory,
) : AppEvent {
    override val name = "expense_create"
    override val level = Level.INFO
    override val fields = mapOf("expenseId" to expenseId, "amount" to amount, "category" to category.name)
}

data class ExpenseUpdated(val expenseId: Long) : AppEvent {
    override val name = "expense_update"
    override val level = Level.INFO
    override val fields = mapOf("expenseId" to expenseId)
}
