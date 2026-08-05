package com.example.donations

import com.example.donations.expense.ExpenseCategory
import com.example.donations.infrastructure.events.AccountLocked
import com.example.donations.infrastructure.events.AdminAction
import com.example.donations.infrastructure.events.AdminActionType
import com.example.donations.infrastructure.events.AuthorizationChanged
import com.example.donations.infrastructure.events.AuthorizationFailed
import com.example.donations.infrastructure.events.DonationCreated
import com.example.donations.infrastructure.events.DonationUpdated
import com.example.donations.infrastructure.events.DonorCreated
import com.example.donations.infrastructure.events.DonorUpdated
import com.example.donations.infrastructure.events.ExpenseCreated
import com.example.donations.infrastructure.events.ExpenseUpdated
import com.example.donations.infrastructure.events.LoginFailed
import com.example.donations.infrastructure.events.LoginSucceeded
import com.example.donations.infrastructure.events.PasswordChangeFailed
import com.example.donations.infrastructure.events.PasswordChanged
import com.example.donations.infrastructure.events.UnexpectedError
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.slf4j.event.Level
import java.math.BigDecimal
import kotlin.test.assertEquals

@DisplayName("App Event Tests")
class AppEventTest {

    @Test
    @DisplayName("Login success uses the OWASP event name")
    fun loginSucceededName() {
        assertEquals("authn_login_success", LoginSucceeded("admin", "10.0.0.1").name)
    }

    @Test
    @DisplayName("Login success is logged at INFO")
    fun loginSucceededLevel() {
        assertEquals(Level.INFO, LoginSucceeded("admin", "10.0.0.1").level)
    }

    @Test
    @DisplayName("Login success carries the userid and source IP and nothing else")
    fun loginSucceededFields() {
        assertEquals(
            mapOf("userid" to "admin", "sourceIp" to "10.0.0.1"),
            LoginSucceeded("admin", "10.0.0.1").fields,
        )
    }

    @Test
    @DisplayName("Login failure uses the OWASP event name")
    fun loginFailedName() {
        assertEquals("authn_login_fail", LoginFailed("admin", "10.0.0.1", locked = false).name)
    }

    @Test
    @DisplayName("Login failure is logged at WARN")
    fun loginFailedLevel() {
        assertEquals(Level.WARN, LoginFailed("admin", "10.0.0.1", locked = false).level)
    }

    @Test
    @DisplayName("Login failure carries the userid, source IP and locked flag")
    fun loginFailedFields() {
        assertEquals(
            mapOf("userid" to "admin", "sourceIp" to "10.0.0.1", "locked" to true),
            LoginFailed("admin", "10.0.0.1", locked = true).fields,
        )
    }

    @Test
    @DisplayName("Account lock uses the OWASP event name")
    fun accountLockedName() {
        assertEquals("authn_login_lock", AccountLocked("admin", "maxretries", 5).name)
    }

    @Test
    @DisplayName("Account lock is logged at WARN")
    fun accountLockedLevel() {
        assertEquals(Level.WARN, AccountLocked("admin", "maxretries", 5).level)
    }

    @Test
    @DisplayName("Account lock carries the userid, reason and limit")
    fun accountLockedFields() {
        assertEquals(
            mapOf("userid" to "admin", "reason" to "maxretries", "maxlimit" to 5),
            AccountLocked("admin", "maxretries", 5).fields,
        )
    }

    @Test
    @DisplayName("Password change uses the OWASP event name")
    fun passwordChangedName() {
        assertEquals("authn_password_change", PasswordChanged("admin").name)
    }

    @Test
    @DisplayName("Password change is logged at INFO")
    fun passwordChangedLevel() {
        assertEquals(Level.INFO, PasswordChanged("admin").level)
    }

    @Test
    @DisplayName("Password change carries the userid and nothing else")
    fun passwordChangedFields() {
        assertEquals(mapOf("userid" to "admin"), PasswordChanged("admin").fields)
    }

    @Test
    @DisplayName("Password change failure uses the OWASP event name")
    fun passwordChangeFailedName() {
        assertEquals("authn_password_change_fail", PasswordChangeFailed("admin").name)
    }

    @Test
    @DisplayName("Password change failure is logged at ERROR (OWASP CRITICAL has no SLF4J level)")
    fun passwordChangeFailedLevel() {
        assertEquals(Level.ERROR, PasswordChangeFailed("admin").level)
    }

    @Test
    @DisplayName("Password change failure carries the userid and nothing else")
    fun passwordChangeFailedFields() {
        assertEquals(mapOf("userid" to "admin"), PasswordChangeFailed("admin").fields)
    }

    @Test
    @DisplayName("Authorization failure uses the OWASP event name")
    fun authorizationFailedName() {
        assertEquals("authz_fail", AuthorizationFailed("operator1", "/api/v1/users").name)
    }

    @Test
    @DisplayName("Authorization failure is logged at ERROR (OWASP CRITICAL has no SLF4J level)")
    fun authorizationFailedLevel() {
        assertEquals(Level.ERROR, AuthorizationFailed("operator1", "/api/v1/users").level)
    }

    @Test
    @DisplayName("Authorization failure carries the userid and requested resource")
    fun authorizationFailedFields() {
        assertEquals(
            mapOf("userid" to "operator1", "resource" to "/api/v1/users"),
            AuthorizationFailed("operator1", "/api/v1/users").fields,
        )
    }

    @Test
    @DisplayName("Admin action uses the OWASP event name")
    fun adminActionName() {
        assertEquals("authz_admin", AdminAction("admin", AdminActionType.USER_CREATE, 42).name)
    }

    @Test
    @DisplayName("Admin action is logged at WARN")
    fun adminActionLevel() {
        assertEquals(Level.WARN, AdminAction("admin", AdminActionType.USER_CREATE, 42).level)
    }

    @Test
    @DisplayName("Admin action carries the acting admin, a fixed description and the target id")
    fun adminActionFields() {
        assertEquals(
            mapOf("userid" to "admin", "action" to "user_create", "targetId" to 42L),
            AdminAction("admin", AdminActionType.USER_CREATE, 42).fields,
        )
    }


    @Test
    @DisplayName("Authorization change uses the OWASP event name")
    fun authorizationChangedName() {
        assertEquals("authz_change", AuthorizationChanged("operator1", "OPERATOR", "ADMIN", 42).name)
    }

    @Test
    @DisplayName("Authorization change is logged at WARN")
    fun authorizationChangedLevel() {
        assertEquals(Level.WARN, AuthorizationChanged("operator1", "OPERATOR", "ADMIN", 42).level)
    }

    @Test
    @DisplayName("Authorization change carries the userid, both privilege levels and the target id")
    fun authorizationChangedFields() {
        assertEquals(
            mapOf("userid" to "operator1", "from" to "OPERATOR", "to" to "ADMIN", "targetId" to 42L),
            AuthorizationChanged("operator1", "OPERATOR", "ADMIN", 42).fields,
        )
    }

    @Test
    @DisplayName("Donation create uses the noun_verb event name")
    fun donationCreatedName() {
        assertEquals("donation_create", DonationCreated(1, 2, BigDecimal("100.00")).name)
    }

    @Test
    @DisplayName("Donation create is logged at INFO")
    fun donationCreatedLevel() {
        assertEquals(Level.INFO, DonationCreated(1, 2, BigDecimal("100.00")).level)
    }

    @Test
    @DisplayName("Donation create carries ids and amount, and nothing donor-shaped")
    fun donationCreatedFields() {
        assertEquals(
            mapOf("donationId" to 1L, "donorId" to 2L, "amount" to BigDecimal("100.00")),
            DonationCreated(1, 2, BigDecimal("100.00")).fields,
        )
    }

    @Test
    @DisplayName("Donation create records a null donor id for an anonymous donation")
    fun donationCreatedAnonymous() {
        assertEquals(
            mapOf("donationId" to 1L, "donorId" to null, "amount" to BigDecimal("50.00")),
            DonationCreated(1, null, BigDecimal("50.00")).fields,
        )
    }

    @Test
    @DisplayName("Donation update uses the noun_verb event name")
    fun donationUpdatedName() {
        assertEquals("donation_update", DonationUpdated(1).name)
    }

    @Test
    @DisplayName("Donation update is logged at INFO")
    fun donationUpdatedLevel() {
        assertEquals(Level.INFO, DonationUpdated(1).level)
    }

    @Test
    @DisplayName("Donation update carries the donation id and nothing else")
    fun donationUpdatedFields() {
        assertEquals(mapOf("donationId" to 1L), DonationUpdated(1).fields)
    }

    @Test
    @DisplayName("Donor create uses the noun_verb event name")
    fun donorCreatedName() {
        assertEquals("donor_create", DonorCreated(7).name)
    }

    @Test
    @DisplayName("Donor create is logged at INFO")
    fun donorCreatedLevel() {
        assertEquals(Level.INFO, DonorCreated(7).level)
    }

    @Test
    @DisplayName("Donor create carries the donor id and nothing else")
    fun donorCreatedFields() {
        assertEquals(mapOf("donorId" to 7L), DonorCreated(7).fields)
    }

    @Test
    @DisplayName("Donor update uses the noun_verb event name")
    fun donorUpdatedName() {
        assertEquals("donor_update", DonorUpdated(7).name)
    }

    @Test
    @DisplayName("Donor update is logged at INFO")
    fun donorUpdatedLevel() {
        assertEquals(Level.INFO, DonorUpdated(7).level)
    }

    @Test
    @DisplayName("Donor update carries the donor id and nothing else")
    fun donorUpdatedFields() {
        assertEquals(mapOf("donorId" to 7L), DonorUpdated(7).fields)
    }

    @Test
    @DisplayName("Expense create uses the noun_verb event name")
    fun expenseCreatedName() {
        assertEquals("expense_create", ExpenseCreated(3, BigDecimal("40.00"), ExpenseCategory.SUPPLIES).name)
    }

    @Test
    @DisplayName("Expense create is logged at INFO")
    fun expenseCreatedLevel() {
        assertEquals(Level.INFO, ExpenseCreated(3, BigDecimal("40.00"), ExpenseCategory.SUPPLIES).level)
    }

    @Test
    @DisplayName("Expense create carries the id, amount and the category name")
    fun expenseCreatedFields() {
        assertEquals(
            mapOf("expenseId" to 3L, "amount" to BigDecimal("40.00"), "category" to "SUPPLIES"),
            ExpenseCreated(3, BigDecimal("40.00"), ExpenseCategory.SUPPLIES).fields,
        )
    }

    @Test
    @DisplayName("Expense update uses the noun_verb event name")
    fun expenseUpdatedName() {
        assertEquals("expense_update", ExpenseUpdated(3).name)
    }

    @Test
    @DisplayName("Expense update is logged at INFO")
    fun expenseUpdatedLevel() {
        assertEquals(Level.INFO, ExpenseUpdated(3).level)
    }

    @Test
    @DisplayName("Expense update carries the expense id and nothing else")
    fun expenseUpdatedFields() {
        assertEquals(mapOf("expenseId" to 3L), ExpenseUpdated(3).fields)
    }

    @Test
    @DisplayName("Unexpected error uses the OWASP event name")
    fun unexpectedErrorName() {
        assertEquals("error_unexpected", UnexpectedError("java.lang.IllegalStateException", "/api/v1/donors").name)
    }

    @Test
    @DisplayName("Unexpected error is logged at ERROR")
    fun unexpectedErrorLevel() {
        assertEquals(Level.ERROR, UnexpectedError("java.lang.IllegalStateException", "/api/v1/donors").level)
    }

    @Test
    @DisplayName("Unexpected error carries the exception type and resource, never a message")
    fun unexpectedErrorFields() {
        assertEquals(
            mapOf("exceptionType" to "java.lang.IllegalStateException", "resource" to "/api/v1/donors"),
            UnexpectedError("java.lang.IllegalStateException", "/api/v1/donors").fields,
        )
    }
}
