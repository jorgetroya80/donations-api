package com.example.donations

import com.example.donations.infrastructure.audit.AuditEntityType
import com.example.donations.infrastructure.audit.AuditLogEntry
import com.example.donations.infrastructure.audit.AuditLogRepository
import com.example.donations.infrastructure.audit.AuditOperation
import com.example.donations.infrastructure.audit.AuditService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.IllegalTransactionStateException
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Audit Log Tests")
class AuditLogTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var auditLogRepository: AuditLogRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var auditService: AuditService

    private lateinit var adminSession: MockHttpSession
    private lateinit var operatorSession: MockHttpSession
    private var operatorId: Long = 0
    private var donorId: Long = 0

    @BeforeEach
    fun setUp() {
        adminSession = TestAuth.loginAsAdmin(mockMvc)
        operatorId = createUser("operator1", "OPERATOR")
        operatorSession = TestAuth.loginActivated(mockMvc, "operator1", "password123")
        donorId = createDonor(operatorSession, "Juan Garcia", "12345678Z")
    }

    // --- Donation ---

    @Test
    @DisplayName("Creating a donation writes one CREATE entry by the operator")
    fun createDonationWritesCreateEntry() {
        val donationId = createDonation(operatorSession)

        val entry = historyOf(AuditEntityType.DONATION, donationId).single()
        assertEquals(AuditEntityType.DONATION, entry.entityType)
        assertEquals(donationId, entry.entityId)
        assertEquals(AuditOperation.CREATE, entry.operation)
        assertEquals("operator1", entry.performedBy)
        assertEquals(operatorId, entry.performedById)
    }

    @Test
    @DisplayName("Donation history keeps CREATE and every UPDATE in order, per user")
    fun donationHistoryKeepsEveryChangeInOrder() {
        createUser("treasurer1", "TREASURER")
        val treasurerSession = TestAuth.loginActivated(mockMvc, "treasurer1", "password123")

        val donationId = createDonation(operatorSession)
        update(treasurerSession, "/api/v1/donations/$donationId", """{"amount":250.00}""")
        update(operatorSession, "/api/v1/donations/$donationId", """{"notes":"Corrected"}""")

        val history = historyOf(AuditEntityType.DONATION, donationId)
        assertEquals(
            listOf(
                AuditOperation.CREATE to "operator1",
                AuditOperation.UPDATE to "treasurer1",
                AuditOperation.UPDATE to "operator1",
            ),
            history.map { it.operation to it.performedBy },
        )
    }

    @Test
    @DisplayName("Latest donation entry's performedBy matches the row's updated_by")
    fun donationPerformedByMatchesUpdatedBy() {
        createUser("treasurer1", "TREASURER")
        val treasurerSession = TestAuth.loginActivated(mockMvc, "treasurer1", "password123")

        val donationId = createDonation(operatorSession)
        update(treasurerSession, "/api/v1/donations/$donationId", """{"amount":250.00}""")

        val latest = historyOf(AuditEntityType.DONATION, donationId).last()
        assertEquals(AuditOperation.UPDATE, latest.operation)
        assertEquals(updatedByOf("donations", donationId), latest.performedBy)
    }

    @Test
    @DisplayName("Rejected donation create (400) writes no audit entry")
    fun invalidDonationCreateWritesNoEntry() {
        val before = auditLogRepository.count()

        mockMvc.perform(
            post("/api/v1/donations")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"donationDate":"2026-01-15","donationType":"TITHE","paymentMethod":"CASH","donorId":$donorId}""")
        ).andExpect(status().isBadRequest)

        assertEquals(before, auditLogRepository.count())
    }

    @Test
    @DisplayName("Unconfirmed duplicate donation (not saved) writes no audit entry")
    fun unconfirmedDuplicateDonationWritesNoEntry() {
        createDonation(operatorSession)
        val before = auditLogRepository.count()

        mockMvc.perform(
            post("/api/v1/donations")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(donationBody())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.saved").value(false))

        assertEquals(before, auditLogRepository.count())
    }

    @Test
    @DisplayName("Rejected donation update (unknown donor, 404) writes no audit entry")
    fun rejectedDonationUpdateWritesNoEntry() {
        val donationId = createDonation(operatorSession)
        val before = auditLogRepository.count()

        mockMvc.perform(
            put("/api/v1/donations/$donationId")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"donorId":999999}""")
        ).andExpect(status().isNotFound)

        assertEquals(before, auditLogRepository.count())
    }

    @Test
    @DisplayName("Donation update failing at commit rolls back its audit entry")
    fun donationUpdateFailingAtCommitRollsBackAuditEntry() {
        val donationId = createDonation(operatorSession)

        // DECIMAL(10,2) overflow: the audit INSERT runs first (IDENTITY), the donation UPDATE fails at commit
        mockMvc.perform(
            put("/api/v1/donations/$donationId")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":123456789012}""")
        ).andExpect(status().isInternalServerError)

        assertEquals(
            listOf(AuditOperation.CREATE),
            historyOf(AuditEntityType.DONATION, donationId).map { it.operation },
        )
        assertEquals(
            BigDecimal("100.00"),
            jdbcTemplate.queryForObject("select amount from donations where id = ?", BigDecimal::class.java, donationId),
        )
    }

    @Test
    @DisplayName("No-op donation update still records UPDATE by the submitter; updated_by is unchanged")
    fun noOpDonationUpdateRecordsSubmitter() {
        createUser("treasurer1", "TREASURER")
        val treasurerSession = TestAuth.loginActivated(mockMvc, "treasurer1", "password123")

        val donationId = createDonation(operatorSession)
        update(treasurerSession, "/api/v1/donations/$donationId", "{}")

        assertEquals(
            listOf(AuditOperation.CREATE to "operator1", AuditOperation.UPDATE to "treasurer1"),
            historyOf(AuditEntityType.DONATION, donationId).map { it.operation to it.performedBy },
        )
        assertEquals("operator1", updatedByOf("donations", donationId))
    }

    // --- Expense ---

    @Test
    @DisplayName("Expense history records CREATE and UPDATE with each user")
    fun expenseHistoryRecordsCreateAndUpdate() {
        createUser("treasurer1", "TREASURER")
        val treasurerSession = TestAuth.loginActivated(mockMvc, "treasurer1", "password123")

        val expenseId = createExpense(operatorSession)
        update(treasurerSession, "/api/v1/expenses/$expenseId", """{"amount":600.00}""")

        assertEquals(
            listOf(AuditOperation.CREATE to "operator1", AuditOperation.UPDATE to "treasurer1"),
            historyOf(AuditEntityType.EXPENSE, expenseId).map { it.operation to it.performedBy },
        )
        assertEquals("treasurer1", updatedByOf("expenses", expenseId))
    }

    @Test
    @DisplayName("Rejected expense create (400) writes no audit entry")
    fun rejectedExpenseCreateWritesNoEntry() {
        val before = auditLogRepository.count()

        mockMvc.perform(
            post("/api/v1/expenses")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"expenseDate":"2026-01-15","category":"RENT","description":"Monthly rent","paymentMethod":"BANK_TRANSFER"}""")
        ).andExpect(status().isBadRequest)

        assertEquals(before, auditLogRepository.count())
    }

    @Test
    @DisplayName("Rejected expense update (404) writes no audit entry")
    fun rejectedExpenseUpdateWritesNoEntry() {
        val before = auditLogRepository.count()

        mockMvc.perform(
            put("/api/v1/expenses/999999")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":600.00}""")
        ).andExpect(status().isNotFound)

        assertEquals(before, auditLogRepository.count())
    }

    // --- Donor ---

    @Test
    @DisplayName("Donor history records CREATE and UPDATE with each user")
    fun donorHistoryRecordsCreateAndUpdate() {
        createUser("treasurer1", "TREASURER")
        val treasurerSession = TestAuth.loginActivated(mockMvc, "treasurer1", "password123")

        update(treasurerSession, "/api/v1/donors/$donorId", """{"phone":"600123123"}""")

        assertEquals(
            listOf(AuditOperation.CREATE to "operator1", AuditOperation.UPDATE to "treasurer1"),
            historyOf(AuditEntityType.DONOR, donorId).map { it.operation to it.performedBy },
        )
        assertEquals("treasurer1", updatedByOf("donors", donorId))
    }

    @Test
    @DisplayName("Rejected donor update (national ID taken) writes no audit entry")
    fun rejectedDonorUpdateWritesNoEntry() {
        val otherDonorId = createDonor(operatorSession, "Ana Ruiz", "87654321X")
        val before = auditLogRepository.count()

        mockMvc.perform(
            put("/api/v1/donors/$otherDonorId")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nationalId":"12345678Z"}""")
        ).andExpect(status().isConflict)

        assertEquals(before, auditLogRepository.count())
    }

    @Test
    @DisplayName("Rejected donor create (duplicate national ID) writes no audit entry")
    fun duplicateDonorCreateWritesNoEntry() {
        val before = auditLogRepository.count()

        mockMvc.perform(
            post("/api/v1/donors")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fullName":"Otro Nombre","nationalId":"12345678Z"}""")
        ).andExpect(status().isConflict)

        assertEquals(before, auditLogRepository.count())
    }

    // --- User ---

    @Test
    @DisplayName("Admin creating a user writes one CREATE entry by admin")
    fun createUserWritesCreateEntryByAdmin() {
        val userId = createUser("treasurer1", "TREASURER")

        val entry = historyOf(AuditEntityType.USER, userId).single()
        assertEquals(AuditOperation.CREATE, entry.operation)
        assertEquals("admin", entry.performedBy)
    }

    @Test
    @DisplayName("User history: admin CREATE, own password change UPDATE, admin UPDATE, in order")
    fun userLifecycleHistoryInOrder() {
        val userId = createUser("treasurer1", "TREASURER")
        TestAuth.loginActivated(mockMvc, "treasurer1", "password123")
        update(adminSession, "/api/v1/users/$userId", """{"roles":["OPERATOR"]}""")

        assertEquals(
            listOf(
                AuditOperation.CREATE to "admin",
                AuditOperation.UPDATE to "treasurer1",
                AuditOperation.UPDATE to "admin",
            ),
            historyOf(AuditEntityType.USER, userId).map { it.operation to it.performedBy },
        )
    }

    @Test
    @DisplayName("Self-service password change with wrong current password (400) writes no audit entry")
    fun wrongCurrentPasswordWritesNoEntry() {
        createUser("treasurer1", "TREASURER")
        val session = TestAuth.login(mockMvc, "treasurer1", "password123")
        val before = auditLogRepository.count()

        mockMvc.perform(
            put("/api/v1/users/me/password")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"wrongcurrent","newPassword":"newpassword1"}""")
        ).andExpect(status().isBadRequest)

        assertEquals(before, auditLogRepository.count())
    }

    @Test
    @DisplayName("Latest user entry's performedBy matches the row's updated_by")
    fun userPerformedByMatchesUpdatedBy() {
        val userId = createUser("treasurer1", "TREASURER")
        TestAuth.loginActivated(mockMvc, "treasurer1", "password123")
        update(adminSession, "/api/v1/users/$userId", """{"active":false}""")

        val latest = historyOf(AuditEntityType.USER, userId).last()
        assertEquals(AuditOperation.UPDATE, latest.operation)
        assertEquals("admin", latest.performedBy)
        assertEquals(updatedByOf("users", userId), latest.performedBy)
    }

    @Test
    @DisplayName("Self-service password change writes one UPDATE entry by that user")
    fun ownPasswordChangeWritesUpdateByUser() {
        val userId = createUser("treasurer1", "TREASURER")
        val session = TestAuth.login(mockMvc, "treasurer1", "password123")

        mockMvc.perform(
            put("/api/v1/users/me/password")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"password123","newPassword":"newpassword1"}""")
        ).andExpect(status().isNoContent)

        assertEquals(
            listOf(AuditOperation.CREATE to "admin", AuditOperation.UPDATE to "treasurer1"),
            historyOf(AuditEntityType.USER, userId).map { it.operation to it.performedBy },
        )
    }

    @Test
    @DisplayName("Rejected user create (username taken, 409) writes no audit entry")
    fun rejectedUserCreateWritesNoEntry() {
        val before = auditLogRepository.count()

        mockMvc.perform(
            post("/api/v1/users")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"operator1","password":"password123","roles":["OPERATOR"]}""")
        ).andExpect(status().isConflict)

        assertEquals(before, auditLogRepository.count())
    }

    @Test
    @DisplayName("Rejected user update (unknown id, 404) writes no audit entry")
    fun rejectedUserUpdateWritesNoEntry() {
        val before = auditLogRepository.count()

        mockMvc.perform(
            put("/api/v1/users/999999")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"active":false}""")
        ).andExpect(status().isNotFound)

        assertEquals(before, auditLogRepository.count())
    }

    @Test
    @DisplayName("Renamed username reused by a new user: old entries still point to the original account")
    fun renamedAndReusedUsernameKeepsOriginalAccountId() {
        val originalId = createUser("treasurer1", "TREASURER")
        val treasurerSession = TestAuth.loginActivated(mockMvc, "treasurer1", "password123")
        val donationId = createDonation(treasurerSession)

        update(adminSession, "/api/v1/users/$originalId", """{"username":"treasurer-old"}""")
        val newcomerId = createUser("treasurer1", "TREASURER")

        val entry = historyOf(AuditEntityType.DONATION, donationId).single()
        assertEquals("treasurer1", entry.performedBy)
        assertEquals(originalId, entry.performedById)
        assertTrue(newcomerId != entry.performedById)
    }

    // --- Schema constraints ---

    @Test
    @DisplayName("Database rejects unknown entity_type and operation values")
    fun databaseRejectsUnknownEnumValues() {
        val insert = "insert into audit_log (entity_type, entity_id, operation, performed_by, performed_at) values (?, 1, ?, 'seed', now())"

        assertFailsWith<DataIntegrityViolationException> { jdbcTemplate.update(insert, "DONATIONS", "CREATE") }
        assertFailsWith<DataIntegrityViolationException> { jdbcTemplate.update(insert, "DONATION", "DELETE") }
    }

    // --- Transaction guard ---

    @Test
    @DisplayName("AuditService refuses to record outside a transaction")
    fun recordOutsideTransactionFails() {
        assertFailsWith<IllegalTransactionStateException> {
            auditService.record(AuditEntityType.DONATION, 1, AuditOperation.CREATE)
        }
    }

    // --- Helpers ---

    private fun createExpense(session: MockHttpSession): Long {
        val result = mockMvc.perform(
            post("/api/v1/expenses")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":500.00,"expenseDate":"2026-01-15","category":"RENT","description":"Monthly rent","paymentMethod":"BANK_TRANSFER"}""")
        ).andExpect(status().isCreated).andReturn()
        return extractId(result.response.contentAsString)
    }

    private fun update(session: MockHttpSession, path: String, body: String) {
        mockMvc.perform(
            put(path)
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isOk)
    }

    private fun historyOf(type: AuditEntityType, id: Long): List<AuditLogEntry> =
        auditLogRepository.findByEntityTypeAndEntityIdOrderByIdAsc(type, id)

    private fun updatedByOf(table: String, id: Long): String? =
        jdbcTemplate.queryForObject("select updated_by from $table where id = ?", String::class.java, id)

    private fun createUser(username: String, role: String): Long {
        val result = mockMvc.perform(
            post("/api/v1/users")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"$username","password":"password123","roles":["$role"]}""")
        ).andExpect(status().isCreated).andReturn()
        return extractId(result.response.contentAsString)
    }

    private fun extractId(responseBody: String): Long {
        val match = """"id"\s*:\s*(\d+)""".toRegex().find(responseBody)
            ?: throw AssertionError("No id found in response: $responseBody")
        return match.groupValues[1].toLong()
    }

    private fun createDonor(session: MockHttpSession, fullName: String, nationalId: String): Long {
        val result = mockMvc.perform(
            post("/api/v1/donors")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fullName":"$fullName","nationalId":"$nationalId"}""")
        ).andExpect(status().isCreated).andReturn()
        return extractId(result.response.contentAsString)
    }

    private fun donationBody(): String =
        """{"amount":100.00,"donationDate":"2026-01-15","donationType":"TITHE","paymentMethod":"CASH","donorId":$donorId}"""

    private fun createDonation(session: MockHttpSession): Long {
        val result = mockMvc.perform(
            post("/api/v1/donations")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(donationBody())
        ).andExpect(status().isCreated).andReturn()
        return extractId(result.response.contentAsString)
    }
}
