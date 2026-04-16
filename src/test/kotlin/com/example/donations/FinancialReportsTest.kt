package com.example.donations

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Financial Reports Tests")
class FinancialReportsTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    private lateinit var treasurerSession: MockHttpSession
    private lateinit var adminSession: MockHttpSession
    private lateinit var operatorSession: MockHttpSession
    private var donorId: Long = 0

    @BeforeEach
    fun setUp() {
        val adminResult = login("admin", "admin")
        adminSession = extractSession(adminResult)

        // Create treasurer
        mockMvc.perform(
            post("/api/v1/users")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"treasurer1","password":"password123","roles":["TREASURER"]}""")
        ).andExpect(status().isCreated)

        // Create operator
        mockMvc.perform(
            post("/api/v1/users")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"operator1","password":"password123","roles":["OPERATOR"]}""")
        ).andExpect(status().isCreated)

        treasurerSession = extractSession(login("treasurer1", "password123"))
        operatorSession = extractSession(login("operator1", "password123"))

        // Create donor
        donorId = createDonor(operatorSession, "Maria Lopez", "87654321X")

        // Seed test data: donations
        createDonation(operatorSession, 100.00, "2026-01-15", "TITHE", "CASH", donorId)
        createDonation(operatorSession, 200.00, "2026-02-10", "OFFERING", "BANK_TRANSFER", donorId)
        createDonation(operatorSession, 50.00, "2026-03-05", "TITHE", "CASH", null) // anonymous

        // Seed test data: expenses
        createExpense(operatorSession, 500.00, "2026-01-15", "RENT", "Monthly rent", "BANK_TRANSFER")
        createExpense(operatorSession, 80.00, "2026-02-20", "UTILITIES", "Electricity bill", "BANK_TRANSFER")
    }

    // --- Donation summary ---

    @Test
    @DisplayName("Donation summary returns totals grouped by type")
    fun donationSummaryReturnsTotalsByType() {
        mockMvc.perform(
            get("/api/v1/reports/donations")
                .session(treasurerSession)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.from").value("2026-01-01"))
            .andExpect(jsonPath("$.to").value("2026-12-31"))
            .andExpect(jsonPath("$.grandTotal").value(350.00))
            .andExpect(jsonPath("$.totalsByType").isArray)
            .andExpect(jsonPath("$.totalsByType.length()").value(2))
    }

    @Test
    @DisplayName("Donation summary with date range returns filtered totals")
    fun donationSummaryWithDateRangeReturnsFiltered() {
        mockMvc.perform(
            get("/api/v1/reports/donations")
                .session(treasurerSession)
                .param("from", "2026-01-01")
                .param("to", "2026-01-31")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.grandTotal").value(100.00))
            .andExpect(jsonPath("$.totalsByType.length()").value(1))
    }

    // --- Expense summary ---

    @Test
    @DisplayName("Expense summary returns totals grouped by category")
    fun expenseSummaryReturnsTotalsByCategory() {
        mockMvc.perform(
            get("/api/v1/reports/expenses")
                .session(treasurerSession)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.from").value("2026-01-01"))
            .andExpect(jsonPath("$.to").value("2026-12-31"))
            .andExpect(jsonPath("$.grandTotal").value(580.00))
            .andExpect(jsonPath("$.totalsByCategory").isArray)
            .andExpect(jsonPath("$.totalsByCategory.length()").value(2))
    }

    @Test
    @DisplayName("Expense summary with date range returns filtered totals")
    fun expenseSummaryWithDateRangeReturnsFiltered() {
        mockMvc.perform(
            get("/api/v1/reports/expenses")
                .session(treasurerSession)
                .param("from", "2026-02-01")
                .param("to", "2026-02-28")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.grandTotal").value(80.00))
            .andExpect(jsonPath("$.totalsByCategory.length()").value(1))
    }

    // --- Balance ---

    @Test
    @DisplayName("Balance report returns income, expenses, and net balance")
    fun balanceReportReturnsCorrectTotals() {
        mockMvc.perform(
            get("/api/v1/reports/balance")
                .session(treasurerSession)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.from").value("2026-01-01"))
            .andExpect(jsonPath("$.to").value("2026-12-31"))
            .andExpect(jsonPath("$.totalIncome").value(350.00))
            .andExpect(jsonPath("$.totalExpenses").value(580.00))
            .andExpect(jsonPath("$.netBalance").value(-230.00))
    }

    // --- Donor statement ---

    @Test
    @DisplayName("Donor statement returns all donations for specific donor")
    fun donorStatementReturnsDonorContributions() {
        mockMvc.perform(
            get("/api/v1/reports/donors/$donorId/statement")
                .session(treasurerSession)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.donorId").value(donorId))
            .andExpect(jsonPath("$.donorName").value("Maria Lopez"))
            .andExpect(jsonPath("$.donations").isArray)
            .andExpect(jsonPath("$.donations.length()").value(2))
            .andExpect(jsonPath("$.total").value(300.00))
    }

    @Test
    @DisplayName("Donor statement with date range returns filtered donations")
    fun donorStatementWithDateRangeReturnsFiltered() {
        mockMvc.perform(
            get("/api/v1/reports/donors/$donorId/statement")
                .session(treasurerSession)
                .param("from", "2026-01-01")
                .param("to", "2026-01-31")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.donations.length()").value(1))
            .andExpect(jsonPath("$.total").value(100.00))
    }

    @Test
    @DisplayName("Donor statement for non-existent donor returns 404")
    fun donorStatementNonExistentDonorReturns404() {
        mockMvc.perform(
            get("/api/v1/reports/donors/999999/statement")
                .session(treasurerSession)
        ).andExpect(status().isNotFound)
    }

    // --- Role enforcement ---

    @Test
    @DisplayName("Treasurer can access report endpoints")
    fun treasurerCanAccessReports() {
        mockMvc.perform(
            get("/api/v1/reports/donations")
                .session(treasurerSession)
        ).andExpect(status().isOk)
    }

    @Test
    @DisplayName("Pastor can access report endpoints")
    fun pastorCanAccessReports() {
        mockMvc.perform(
            post("/api/v1/users")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"pastor1","password":"password123","roles":["PASTOR"]}""")
        ).andExpect(status().isCreated)

        val pastorSession = extractSession(login("pastor1", "password123"))

        mockMvc.perform(
            get("/api/v1/reports/donations")
                .session(pastorSession)
        ).andExpect(status().isOk)
    }

    @Test
    @DisplayName("Admin accessing report endpoints returns 403")
    fun adminAccessingReportsReturns403() {
        mockMvc.perform(
            get("/api/v1/reports/donations")
                .session(adminSession)
        ).andExpect(status().isForbidden)
    }

    @Test
    @DisplayName("Operator accessing report endpoints returns 403")
    fun operatorAccessingReportsReturns403() {
        mockMvc.perform(
            get("/api/v1/reports/donations")
                .session(operatorSession)
        ).andExpect(status().isForbidden)
    }

    // --- Helpers ---

    private fun login(username: String, password: String): MvcResult {
        return mockMvc.perform(
            post("/api/v1/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"$username","password":"$password"}""")
        ).andReturn()
    }

    private fun extractSession(result: MvcResult): MockHttpSession {
        return result.request.getSession(false) as? MockHttpSession
            ?: throw AssertionError("No session created after login")
    }

    private fun extractId(responseBody: String): Long {
        val match = """"id"\s*:\s*(\d+)""".toRegex().find(responseBody)
            ?: throw AssertionError("No id found in response: $responseBody")
        return match.groupValues[1].toLong()
    }

    private fun createDonor(session: MockHttpSession, fullName: String, dniNie: String): Long {
        val result = mockMvc.perform(
            post("/api/v1/donors")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fullName":"$fullName","dniNie":"$dniNie"}""")
        ).andExpect(status().isCreated).andReturn()
        return extractId(result.response.contentAsString)
    }

    private fun createDonation(
        session: MockHttpSession,
        amount: Double,
        date: String,
        donationType: String,
        paymentMethod: String,
        donorId: Long?,
    ): Long {
        val donorField = if (donorId != null) ""","donorId":$donorId""" else ""
        val result = mockMvc.perform(
            post("/api/v1/donations")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":$amount,"donationDate":"$date","donationType":"$donationType","paymentMethod":"$paymentMethod"$donorField}""")
        ).andExpect(status().isCreated).andReturn()
        return extractId(result.response.contentAsString)
    }

    private fun createExpense(
        session: MockHttpSession,
        amount: Double,
        date: String,
        category: String,
        description: String,
        paymentMethod: String,
    ): Long {
        val result = mockMvc.perform(
            post("/api/v1/expenses")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":$amount,"expenseDate":"$date","category":"$category","description":"$description","paymentMethod":"$paymentMethod"}""")
        ).andExpect(status().isCreated).andReturn()
        return extractId(result.response.contentAsString)
    }
}
