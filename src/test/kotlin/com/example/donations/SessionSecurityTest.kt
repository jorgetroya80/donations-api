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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Session Invalidation Tests")
class SessionSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private lateinit var adminSession: MockHttpSession
    private var userId: String = ""

    @BeforeEach
    fun setUp() {
        adminSession = TestAuth.loginAsAdmin(mockMvc)

        val result = mockMvc.perform(
            post("/api/v1/users")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"twosessions","password":"password123","roles":["OPERATOR"]}""")
        ).andReturn()
        userId = """"id"\s*:\s*(\d+)""".toRegex().find(result.response.contentAsString)?.groupValues?.get(1)
            ?: throw AssertionError("Could not extract id: ${result.response.contentAsString}")
    }

    @Test
    @DisplayName("Self-service password change invalidates other sessions but keeps the current one")
    fun passwordChangeInvalidatesOtherSessions() {
        val sessionA = TestAuth.loginActivated(mockMvc, "twosessions", "password123")
        val sessionB = TestAuth.login(mockMvc, "twosessions", "password123")

        mockMvc.perform(get("/api/v1/donors").session(sessionB)).andExpect(status().isOk)

        mockMvc.perform(
            put("/api/v1/users/me/password")
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"password123","newPassword":"rotated-pass-9"}""")
        ).andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/donors").session(sessionB)).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/v1/donors").session(sessionA)).andExpect(status().isOk)
    }

    @Test
    @DisplayName("Admin password reset invalidates all sessions of the target user")
    fun adminPasswordResetInvalidatesTargetSessions() {
        val userSession = TestAuth.loginActivated(mockMvc, "twosessions", "password123")
        mockMvc.perform(get("/api/v1/donors").session(userSession)).andExpect(status().isOk)

        mockMvc.perform(
            put("/api/v1/users/$userId")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"password":"adminreset1"}""")
        ).andExpect(status().isOk)

        mockMvc.perform(get("/api/v1/donors").session(userSession)).andExpect(status().isUnauthorized)
    }
}
