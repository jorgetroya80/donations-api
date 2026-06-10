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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Expense Recording Tests")
class ExpenseRecordingTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    private lateinit var operatorSession: MockHttpSession
    private lateinit var adminSession: MockHttpSession

    @BeforeEach
    fun setUp() {
        adminSession = TestAuth.loginAsAdmin(mockMvc)

        mockMvc.perform(
            post("/api/v1/users")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"operator1","password":"password123","roles":["OPERATOR"]}""")
        ).andExpect(status().isCreated)

        operatorSession = TestAuth.loginActivated(mockMvc, "operator1", "password123")
    }

    // --- CRUD tests ---

    @Test
    @DisplayName("Create expense with valid data returns 201")
    fun createExpenseWithValidDataReturns201() {
        mockMvc.perform(
            post("/api/v1/expenses")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":500.00,"expenseDate":"2026-01-15","category":"RENT","description":"Monthly rent","vendor":"Landlord Inc","paymentMethod":"BANK_TRANSFER"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isNumber)
            .andExpect(jsonPath("$.amount").value(500.00))
            .andExpect(jsonPath("$.category").value("RENT"))
            .andExpect(jsonPath("$.description").value("Monthly rent"))
            .andExpect(jsonPath("$.vendor").value("Landlord Inc"))
            .andExpect(jsonPath("$.paymentMethod").value("BANK_TRANSFER"))
    }

    @Test
    @DisplayName("Create expense without vendor returns 201")
    fun createExpenseWithoutVendorReturns201() {
        mockMvc.perform(
            post("/api/v1/expenses")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":75.00,"expenseDate":"2026-01-15","category":"SUPPLIES","description":"Office supplies","paymentMethod":"CASH"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.vendor").doesNotExist())
    }

    @Test
    @DisplayName("Create expense with zero amount returns 400")
    fun createExpenseWithZeroAmountReturns400() {
        mockMvc.perform(
            post("/api/v1/expenses")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":0,"expenseDate":"2026-01-15","category":"RENT","description":"Rent","paymentMethod":"CASH"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("Create expense with negative amount returns 400")
    fun createExpenseWithNegativeAmountReturns400() {
        mockMvc.perform(
            post("/api/v1/expenses")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":-10.00,"expenseDate":"2026-01-15","category":"RENT","description":"Rent","paymentMethod":"CASH"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("Create expense with future date returns 400")
    fun createExpenseWithFutureDateReturns400() {
        mockMvc.perform(
            post("/api/v1/expenses")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":100.00,"expenseDate":"2099-12-31","category":"RENT","description":"Rent","paymentMethod":"CASH"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("Create expense without description returns 400")
    fun createExpenseWithoutDescriptionReturns400() {
        mockMvc.perform(
            post("/api/v1/expenses")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":100.00,"expenseDate":"2026-01-15","category":"RENT","paymentMethod":"CASH"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("List expenses returns paginated response")
    fun listExpensesReturnsPaginatedResponse() {
        createExpense(operatorSession, 500.00, "2026-01-15", "RENT", "Monthly rent", "BANK_TRANSFER")
        createExpense(operatorSession, 75.00, "2026-01-20", "SUPPLIES", "Office supplies", "CASH")

        mockMvc.perform(
            get("/api/v1/expenses")
                .session(operatorSession)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(2))
    }

    @Test
    @DisplayName("List expenses with date range filter returns filtered results")
    fun listExpensesWithDateRangeReturnsFiltered() {
        createExpense(operatorSession, 500.00, "2026-01-15", "RENT", "January rent", "BANK_TRANSFER")
        createExpense(operatorSession, 500.00, "2026-03-15", "RENT", "March rent", "BANK_TRANSFER")
        createExpense(operatorSession, 200.00, "2026-04-10", "UTILITIES", "April utilities", "BANK_TRANSFER")

        mockMvc.perform(
            get("/api/v1/expenses")
                .session(operatorSession)
                .param("from", "2026-01-01")
                .param("to", "2026-02-28")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].amount").value(500.00))
    }

    @Test
    @DisplayName("Get expense by ID returns 200")
    fun getExpenseByIdReturns200() {
        val expenseId = createExpense(operatorSession, 150.00, "2026-02-10", "MAINTENANCE", "Fix plumbing", "CASH")

        mockMvc.perform(
            get("/api/v1/expenses/$expenseId")
                .session(operatorSession)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(expenseId))
            .andExpect(jsonPath("$.amount").value(150.00))
            .andExpect(jsonPath("$.category").value("MAINTENANCE"))
    }

    @Test
    @DisplayName("Get non-existent expense returns 404")
    fun getNonExistentExpenseReturns404() {
        mockMvc.perform(
            get("/api/v1/expenses/999999")
                .session(operatorSession)
        ).andExpect(status().isNotFound)
    }

    @Test
    @DisplayName("Update expense amount returns 200")
    fun updateExpenseAmountReturns200() {
        val expenseId = createExpense(operatorSession, 100.00, "2026-01-15", "SUPPLIES", "Supplies", "CASH")

        mockMvc.perform(
            put("/api/v1/expenses/$expenseId")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":250.00}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.amount").value(250.00))
    }

    @Test
    @DisplayName("Update expense description returns 200")
    fun updateExpenseDescriptionReturns200() {
        val expenseId = createExpense(operatorSession, 100.00, "2026-01-15", "SUPPLIES", "Supplies", "CASH")

        mockMvc.perform(
            put("/api/v1/expenses/$expenseId")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"description":"Updated description"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.description").value("Updated description"))
    }

    @Test
    @DisplayName("Expense has audit columns populated")
    fun expenseHasAuditColumnsPopulated() {
        val expenseId = createExpense(operatorSession, 100.00, "2026-01-15", "SUPPLIES", "Supplies", "CASH")

        mockMvc.perform(
            get("/api/v1/expenses/$expenseId")
                .session(operatorSession)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.createdAt").isNotEmpty)
            .andExpect(jsonPath("$.updatedAt").isNotEmpty)
    }

    // --- Role enforcement tests ---

    @Test
    @DisplayName("Admin accessing expense endpoints returns 403")
    fun adminAccessingExpenseEndpointsReturns403() {
        mockMvc.perform(
            get("/api/v1/expenses")
                .session(adminSession)
        ).andExpect(status().isForbidden)
    }

    @Test
    @DisplayName("Pastor accessing expense endpoints returns 403")
    fun pastorAccessingExpenseEndpointsReturns403() {
        mockMvc.perform(
            post("/api/v1/users")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"pastor1","password":"password123","roles":["PASTOR"]}""")
        ).andExpect(status().isCreated)

        val pastorResult = login("pastor1", "password123")
        val pastorSession = extractSession(pastorResult)

        mockMvc.perform(
            get("/api/v1/expenses")
                .session(pastorSession)
        ).andExpect(status().isForbidden)
    }

    @Test
    @DisplayName("Operator can access expense endpoints")
    fun operatorCanAccessExpenseEndpoints() {
        mockMvc.perform(
            get("/api/v1/expenses")
                .session(operatorSession)
        ).andExpect(status().isOk)
    }

    @Test
    @DisplayName("Treasurer can access expense endpoints")
    fun treasurerCanAccessExpenseEndpoints() {
        mockMvc.perform(
            post("/api/v1/users")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"treasurer1","password":"password123","roles":["TREASURER"]}""")
        ).andExpect(status().isCreated)

        val treasurerSession = TestAuth.loginActivated(mockMvc, "treasurer1", "password123")

        mockMvc.perform(
            get("/api/v1/expenses")
                .session(treasurerSession)
        ).andExpect(status().isOk)
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

    private fun createExpense(
        session: MockHttpSession,
        amount: Double,
        date: String,
        category: String,
        description: String,
        paymentMethod: String,
        vendor: String? = null,
    ): Long {
        val vendorField = if (vendor != null) ""","vendor":"$vendor"""" else ""
        val result = mockMvc.perform(
            post("/api/v1/expenses")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":$amount,"expenseDate":"$date","category":"$category","description":"$description","paymentMethod":"$paymentMethod"$vendorField}""")
        ).andExpect(status().isCreated).andReturn()
        return extractId(result.response.contentAsString)
    }
}
