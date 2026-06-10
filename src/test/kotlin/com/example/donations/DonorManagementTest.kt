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
@DisplayName("Donor Management Tests")
class DonorManagementTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    private lateinit var operatorSession: MockHttpSession
    private lateinit var adminSession: MockHttpSession

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

    private fun createUser(session: MockHttpSession, username: String, password: String, role: String) {
        mockMvc.perform(
            post("/api/v1/users")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"$username","password":"$password","roles":["$role"]}""")
        ).andExpect(status().isCreated)
    }

    private fun createDonor(session: MockHttpSession, fullName: String, nationalId: String): MvcResult {
        return mockMvc.perform(
            post("/api/v1/donors")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fullName":"$fullName","nationalId":"$nationalId"}""")
        ).andReturn()
    }

    @BeforeEach
    fun setUp() {
        adminSession = TestAuth.loginAsAdmin(mockMvc)

        createUser(adminSession, "operator", "password123", "OPERATOR")

        operatorSession = TestAuth.loginActivated(mockMvc, "operator", "password123")
    }

    // --- CRUD tests (as OPERATOR) ---

    @Test
    @DisplayName("Create donor with valid DNI returns 201")
    fun createDonorWithValidDniReturns201() {
        mockMvc.perform(
            post("/api/v1/donors")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fullName":"Juan Garcia","nationalId":"12345678Z","email":"juan@example.com","phone":"612345678","address":"Calle Mayor 1"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isNumber)
            .andExpect(jsonPath("$.fullName").value("Juan Garcia"))
            .andExpect(jsonPath("$.nationalId").value("12345678Z"))
            .andExpect(jsonPath("$.email").value("juan@example.com"))
            .andExpect(jsonPath("$.password").doesNotExist())
    }

    @Test
    @DisplayName("Create donor with valid NIE returns 201")
    fun createDonorWithValidNieReturns201() {
        mockMvc.perform(
            post("/api/v1/donors")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fullName":"Maria Lopez","nationalId":"X1234567L"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.fullName").value("Maria Lopez"))
            .andExpect(jsonPath("$.nationalId").value("X1234567L"))
    }

    @Test
    @DisplayName("Create donor with duplicate nationalId returns 409")
    fun createDonorWithDuplicateNationalIdReturns409() {
        createDonor(operatorSession, "Juan Garcia", "12345678Z")

        mockMvc.perform(
            post("/api/v1/donors")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fullName":"Pedro Sanchez","nationalId":"12345678Z"}""")
        ).andExpect(status().isConflict)
    }

    @Test
    @DisplayName("Create donor with blank fullName returns 400 with field errors")
    fun createDonorWithBlankFullNameReturns400() {
        mockMvc.perform(
            post("/api/v1/donors")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fullName":"","nationalId":"12345678Z"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fields").exists())
    }

    @Test
    @DisplayName("Create donor with invalid DNI wrong check letter returns 400")
    fun createDonorWithInvalidDniCheckLetterReturns400() {
        mockMvc.perform(
            post("/api/v1/donors")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fullName":"Invalid DNI","nationalId":"12345678A"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("Create donor with invalid DNI too short returns 400")
    fun createDonorWithTooShortDniReturns400() {
        mockMvc.perform(
            post("/api/v1/donors")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fullName":"Short DNI","nationalId":"1234567Z"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("Create donor with completely invalid format returns 400")
    fun createDonorWithInvalidFormatReturns400() {
        mockMvc.perform(
            post("/api/v1/donors")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fullName":"Bad Format","nationalId":"ABC"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("List donors returns paginated response")
    fun listDonorsReturnsPaginatedResponse() {
        createDonor(operatorSession, "Juan Garcia", "12345678Z")
        createDonor(operatorSession, "Maria Lopez", "X1234567L")

        mockMvc.perform(
            get("/api/v1/donors")
                .session(operatorSession)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.number").value(0))
    }

    @Test
    @DisplayName("Get donor by ID returns 200")
    fun getDonorByIdReturns200() {
        val createResult = createDonor(operatorSession, "Juan Garcia", "12345678Z")
        val donorId = extractId(createResult.response.contentAsString)

        mockMvc.perform(
            get("/api/v1/donors/$donorId")
                .session(operatorSession)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(donorId))
            .andExpect(jsonPath("$.fullName").value("Juan Garcia"))
    }

    @Test
    @DisplayName("Get non-existent donor returns 404")
    fun getNonExistentDonorReturns404() {
        mockMvc.perform(
            get("/api/v1/donors/99999")
                .session(operatorSession)
        ).andExpect(status().isNotFound)
    }

    @Test
    @DisplayName("Update donor fullName returns 200 with changed name")
    fun updateDonorFullNameReturns200() {
        val createResult = createDonor(operatorSession, "Juan Garcia", "12345678Z")
        val donorId = extractId(createResult.response.contentAsString)

        mockMvc.perform(
            put("/api/v1/donors/$donorId")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fullName":"Juan Garcia Martinez"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fullName").value("Juan Garcia Martinez"))
            .andExpect(jsonPath("$.nationalId").value("12345678Z"))
    }

    @Test
    @DisplayName("Deactivate donor returns 200 with active=false")
    fun deactivateDonorReturns200() {
        val createResult = createDonor(operatorSession, "Juan Garcia", "12345678Z")
        val donorId = extractId(createResult.response.contentAsString)

        mockMvc.perform(
            put("/api/v1/donors/$donorId")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"active":false}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.active").value(false))
    }

    @Test
    @DisplayName("Update donor nationalId to duplicate returns 409")
    fun updateDonorNationalIdToDuplicateReturns409() {
        createDonor(operatorSession, "Juan Garcia", "12345678Z")
        val secondResult = createDonor(operatorSession, "Maria Lopez", "X1234567L")
        val secondId = extractId(secondResult.response.contentAsString)

        mockMvc.perform(
            put("/api/v1/donors/$secondId")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nationalId":"12345678Z"}""")
        ).andExpect(status().isConflict)
    }

    // --- Role enforcement tests ---

    @Test
    @DisplayName("Admin accessing donor endpoints returns 403")
    fun adminAccessingDonorEndpointsReturns403() {
        mockMvc.perform(
            get("/api/v1/donors")
                .session(adminSession)
        ).andExpect(status().isForbidden)
    }

    @Test
    @DisplayName("Pastor accessing donor endpoints returns 403")
    fun pastorAccessingDonorEndpointsReturns403() {
        createUser(adminSession, "pastor", "password123", "PASTOR")
        val pastorResult = login("pastor", "password123")
        val pastorSession = extractSession(pastorResult)

        mockMvc.perform(
            get("/api/v1/donors")
                .session(pastorSession)
        ).andExpect(status().isForbidden)
    }

    @Test
    @DisplayName("Operator accessing donor endpoints returns 200")
    fun operatorAccessingDonorEndpointsReturns200() {
        mockMvc.perform(
            get("/api/v1/donors")
                .session(operatorSession)
        ).andExpect(status().isOk)
    }

    @Test
    @DisplayName("Treasurer accessing donor endpoints returns 200")
    fun treasurerAccessingDonorEndpointsReturns200() {
        createUser(adminSession, "treasurer", "password123", "TREASURER")
        val treasurerSession = TestAuth.loginActivated(mockMvc, "treasurer", "password123")

        mockMvc.perform(
            get("/api/v1/donors")
                .session(treasurerSession)
        ).andExpect(status().isOk)
    }
}
