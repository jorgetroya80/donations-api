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
@DisplayName("Donation Recording Tests")
class DonationRecordingTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    private lateinit var operatorSession: MockHttpSession
    private lateinit var adminSession: MockHttpSession
    private var donorId: Long = 0

    @BeforeEach
    fun setUp() {
        val adminResult = login("admin", "admin")
        adminSession = extractSession(adminResult)

        mockMvc.perform(
            post("/api/v1/users")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"operator1","password":"password123","roles":["OPERATOR"]}""")
        ).andExpect(status().isCreated)

        val operatorResult = login("operator1", "password123")
        operatorSession = extractSession(operatorResult)

        donorId = createDonor(operatorSession, "Juan Garcia", "12345678Z")
    }

    // --- CRUD tests ---

    @Test
    @DisplayName("Create donation with valid data and donor returns 201")
    fun createDonationWithValidDataReturns201() {
        mockMvc.perform(
            post("/api/v1/donations")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":100.00,"donationDate":"2026-01-15","donationType":"TITHE","paymentMethod":"CASH","donorId":$donorId}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.donation.id").isNumber)
            .andExpect(jsonPath("$.donation.amount").value(100.00))
            .andExpect(jsonPath("$.donation.donationType").value("TITHE"))
            .andExpect(jsonPath("$.donation.donorId").value(donorId))
            .andExpect(jsonPath("$.saved").value(true))
            .andExpect(jsonPath("$.duplicateWarning").value(false))
    }

    @Test
    @DisplayName("Create anonymous donation without donorId returns 201")
    fun createAnonymousDonationReturns201() {
        mockMvc.perform(
            post("/api/v1/donations")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":50.00,"donationDate":"2026-01-15","donationType":"OFFERING","paymentMethod":"CASH"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.donation.id").isNumber)
            .andExpect(jsonPath("$.donation.donorId").doesNotExist())
            .andExpect(jsonPath("$.saved").value(true))
    }

    @Test
    @DisplayName("Create donation with zero amount returns 400")
    fun createDonationWithZeroAmountReturns400() {
        mockMvc.perform(
            post("/api/v1/donations")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":0,"donationDate":"2026-01-15","donationType":"TITHE","paymentMethod":"CASH","donorId":$donorId}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("Create donation with negative amount returns 400")
    fun createDonationWithNegativeAmountReturns400() {
        mockMvc.perform(
            post("/api/v1/donations")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":-10.00,"donationDate":"2026-01-15","donationType":"TITHE","paymentMethod":"CASH","donorId":$donorId}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("Create donation with future date returns 400")
    fun createDonationWithFutureDateReturns400() {
        mockMvc.perform(
            post("/api/v1/donations")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":100.00,"donationDate":"2099-12-31","donationType":"TITHE","paymentMethod":"CASH","donorId":$donorId}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("List donations returns paginated response")
    fun listDonationsReturnsPaginatedResponse() {
        createDonation(operatorSession, 100.00, "2026-01-15", "TITHE", "CASH", donorId)
        createDonation(operatorSession, 200.00, "2026-01-16", "OFFERING", "BANK_TRANSFER", donorId)

        mockMvc.perform(
            get("/api/v1/donations")
                .session(operatorSession)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(2))
    }

    @Test
    @DisplayName("List donations with date range filter returns filtered results")
    fun listDonationsWithDateRangeReturnsFiltered() {
        createDonation(operatorSession, 100.00, "2026-01-15", "TITHE", "CASH", donorId)
        createDonation(operatorSession, 200.00, "2026-03-20", "OFFERING", "CASH", donorId)
        createDonation(operatorSession, 300.00, "2026-04-10", "SPECIAL_OFFERING", "BANK_TRANSFER", donorId)

        mockMvc.perform(
            get("/api/v1/donations")
                .session(operatorSession)
                .param("from", "2026-01-01")
                .param("to", "2026-02-28")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].amount").value(100.00))
    }

    @Test
    @DisplayName("Get donation by ID returns 200")
    fun getDonationByIdReturns200() {
        val donationId = createDonation(operatorSession, 150.00, "2026-02-10", "TITHE", "CASH", donorId)

        mockMvc.perform(
            get("/api/v1/donations/$donationId")
                .session(operatorSession)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(donationId))
            .andExpect(jsonPath("$.amount").value(150.00))
            .andExpect(jsonPath("$.donorId").value(donorId))
    }

    @Test
    @DisplayName("Get non-existent donation returns 404")
    fun getNonExistentDonationReturns404() {
        mockMvc.perform(
            get("/api/v1/donations/999999")
                .session(operatorSession)
        ).andExpect(status().isNotFound)
    }

    @Test
    @DisplayName("Update donation amount returns 200")
    fun updateDonationAmountReturns200() {
        val donationId = createDonation(operatorSession, 100.00, "2026-01-15", "TITHE", "CASH", donorId)

        mockMvc.perform(
            put("/api/v1/donations/$donationId")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":250.00}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.amount").value(250.00))
    }

    @Test
    @DisplayName("Update donation notes returns 200")
    fun updateDonationNotesReturns200() {
        val donationId = createDonation(operatorSession, 100.00, "2026-01-15", "TITHE", "CASH", donorId)

        mockMvc.perform(
            put("/api/v1/donations/$donationId")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"notes":"Updated notes"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.notes").value("Updated notes"))
    }

    // --- Duplicate detection tests ---

    @Test
    @DisplayName("Duplicate donation without confirm returns 200 with warning")
    fun duplicateDonationWithoutConfirmReturnsWarning() {
        createDonation(operatorSession, 100.00, "2026-01-15", "TITHE", "CASH", donorId)

        mockMvc.perform(
            post("/api/v1/donations")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":100.00,"donationDate":"2026-01-15","donationType":"TITHE","paymentMethod":"CASH","donorId":$donorId}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.duplicateWarning").value(true))
            .andExpect(jsonPath("$.saved").value(false))
    }

    @Test
    @DisplayName("Duplicate donation with confirmDuplicate=true returns 201")
    fun duplicateDonationWithConfirmSaves() {
        createDonation(operatorSession, 100.00, "2026-01-15", "TITHE", "CASH", donorId)

        mockMvc.perform(
            post("/api/v1/donations")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":100.00,"donationDate":"2026-01-15","donationType":"TITHE","paymentMethod":"CASH","donorId":$donorId,"confirmDuplicate":true}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.saved").value(true))
            .andExpect(jsonPath("$.duplicateWarning").value(true))
    }

    @Test
    @DisplayName("Anonymous donation skips duplicate detection")
    fun anonymousDonationSkipsDuplicateDetection() {
        mockMvc.perform(
            post("/api/v1/donations")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":75.00,"donationDate":"2026-01-15","donationType":"OFFERING","paymentMethod":"CASH"}""")
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/v1/donations")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":75.00,"donationDate":"2026-01-15","donationType":"OFFERING","paymentMethod":"CASH"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.saved").value(true))
            .andExpect(jsonPath("$.duplicateWarning").value(false))
    }

    // --- Role enforcement tests ---

    @Test
    @DisplayName("Admin accessing donation endpoints returns 403")
    fun adminAccessingDonationEndpointsReturns403() {
        mockMvc.perform(
            get("/api/v1/donations")
                .session(adminSession)
        ).andExpect(status().isForbidden)
    }

    @Test
    @DisplayName("Pastor accessing donation endpoints returns 403")
    fun pastorAccessingDonationEndpointsReturns403() {
        mockMvc.perform(
            post("/api/v1/users")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"pastor1","password":"password123","roles":["PASTOR"]}""")
        ).andExpect(status().isCreated)

        val pastorResult = login("pastor1", "password123")
        val pastorSession = extractSession(pastorResult)

        mockMvc.perform(
            get("/api/v1/donations")
                .session(pastorSession)
        ).andExpect(status().isForbidden)
    }

    @Test
    @DisplayName("Operator can access donation endpoints")
    fun operatorCanAccessDonationEndpoints() {
        mockMvc.perform(
            get("/api/v1/donations")
                .session(operatorSession)
        ).andExpect(status().isOk)
    }

    @Test
    @DisplayName("Treasurer can access donation endpoints")
    fun treasurerCanAccessDonationEndpoints() {
        mockMvc.perform(
            post("/api/v1/users")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"treasurer1","password":"password123","roles":["TREASURER"]}""")
        ).andExpect(status().isCreated)

        val treasurerResult = login("treasurer1", "password123")
        val treasurerSession = extractSession(treasurerResult)

        mockMvc.perform(
            get("/api/v1/donations")
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
        donorId: Long? = null,
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
}
