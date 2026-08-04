package com.example.donations

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.example.donations.infrastructure.events.RequestIdFilter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Security Integration Tests")
class SecurityIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var filterChainProxy: FilterChainProxy

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

    private fun loginAsAdmin(): MockHttpSession = TestAuth.loginAsAdmin(mockMvc)

    private fun createUser(session: MockHttpSession, username: String, password: String, role: String): MvcResult {
        return mockMvc.perform(
            post("/api/v1/users")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"$username","password":"$password","roles":["$role"]}""")
        ).andReturn()
    }

    @Test
    @DisplayName("Unauthenticated request to protected endpoint returns 401")
    fun unauthenticatedRequestReturns401() {
        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.title").value("Unauthorized"))
            .andExpect(jsonPath("$.detail").value("Authentication required"))
            .andExpect(jsonPath("$.instance").value("/api/v1/users"))
    }

    @Test
    @DisplayName("Login with valid credentials returns 200 and creates session")
    fun loginWithValidCredentialsReturns200() {
        val result = mockMvc.perform(
            post("/api/v1/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"admin","password":"admin"}""")
        )
            .andExpect(status().isOk)
            .andReturn()

        val session = result.request.getSession(false)
        assert(session != null) { "Expected a session to be created" }
    }

    @Test
    @DisplayName("Login with invalid credentials returns 401")
    fun loginWithInvalidCredentialsReturns401() {
        mockMvc.perform(
            post("/api/v1/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"admin","password":"wrongpassword"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.title").value("Unauthorized"))
            .andExpect(jsonPath("$.detail").value("Authentication required"))
    }

    @Test
    @DisplayName("Login with blank credentials returns 400 validation error, not 401")
    fun loginWithBlankCredentialsReturns400() {
        mockMvc.perform(
            post("/api/v1/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"","password":""}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Validation failed"))
            .andExpect(jsonPath("$.fields.username").exists())
            .andExpect(jsonPath("$.fields.password").exists())
    }

    @Test
    @DisplayName("Login rotates session ID and invalidates pre-auth session")
    fun loginRotatesSessionId() {
        val preAuthSession = MockHttpSession()

        val result = mockMvc.perform(
            post("/api/v1/login")
                .session(preAuthSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"admin","password":"admin"}""")
        )
            .andExpect(status().isOk)
            .andReturn()

        val postAuthSession = result.request.getSession(false)
            ?: throw AssertionError("No session after login")

        assert(postAuthSession.id != preAuthSession.id) {
            "Session ID must change on login (fixation protection)"
        }
        assert(preAuthSession.isInvalid) { "Pre-auth session must be invalidated on login" }
    }

    @Test
    @DisplayName("Five failed logins lock the account even for the correct password")
    fun fiveFailedLoginsLockAccount() {
        repeat(5) {
            mockMvc.perform(
                post("/api/v1/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"username":"admin","password":"wrongpassword"}""")
            ).andExpect(status().isUnauthorized)
        }

        mockMvc.perform(
            post("/api/v1/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"admin","password":"admin"}""")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("Successful login resets the failure counter")
    fun successfulLoginResetsFailureCounter() {
        repeat(4) {
            mockMvc.perform(
                post("/api/v1/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"username":"admin","password":"wrongpassword"}""")
            ).andExpect(status().isUnauthorized)
        }

        login("admin", "admin").let { assert(it.response.status == 200) }

        mockMvc.perform(
            post("/api/v1/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"admin","password":"wrongpassword"}""")
        ).andExpect(status().isUnauthorized)

        login("admin", "admin").let { assert(it.response.status == 200) }
    }

    @Test
    @DisplayName("Seeded admin must change password on first login")
    fun seededAdminMustChangePassword() {
        mockMvc.perform(
            post("/api/v1/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"admin","password":"admin"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mustChangePassword").value(true))
    }

    @Test
    @DisplayName("Admin-created user must change password; self-service change clears the flag")
    fun adminProvisionedPasswordIsProvisional() {
        val adminSession = loginAsAdmin()
        createUser(adminSession, "newuser1", "provisional1", "OPERATOR")

        mockMvc.perform(
            post("/api/v1/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"newuser1","password":"provisional1"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mustChangePassword").value(true))

        val userSession = extractSession(login("newuser1", "provisional1"))
        mockMvc.perform(
            put("/api/v1/users/me/password")
                .session(userSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"provisional1","newPassword":"ownpassword1"}""")
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            post("/api/v1/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"newuser1","password":"ownpassword1"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mustChangePassword").value(false))
    }

    @Test
    @DisplayName("Admin password reset flags the target user for password change")
    fun adminPasswordResetFlagsUser() {
        val adminSession = loginAsAdmin()
        val createResult = createUser(adminSession, "resetme", "initialpass1", "OPERATOR")
        val userId = extractId(createResult.response.contentAsString)

        val userSession = extractSession(login("resetme", "initialpass1"))
        mockMvc.perform(
            put("/api/v1/users/me/password")
                .session(userSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"initialpass1","newPassword":"ownpassword1"}""")
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            put("/api/v1/users/$userId")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"password":"adminreset1"}""")
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"resetme","password":"adminreset1"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mustChangePassword").value(true))
    }

    @Test
    @DisplayName("Flagged user is blocked from business endpoints until password change")
    fun flaggedUserBlockedUntilPasswordChange() {
        val session = extractSession(login("admin", "admin"))

        mockMvc.perform(
            get("/api/v1/users")
                .session(session)
        )
            .andExpect(status().isForbidden)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Password change required"))
            .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"))

        mockMvc.perform(
            put("/api/v1/users/me/password")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"admin","newPassword":"rotated-pass-1"}""")
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            get("/api/v1/users")
                .session(session)
        ).andExpect(status().isOk)
    }

    @Test
    @DisplayName("Logout invalidates session")
    fun logoutInvalidatesSession() {
        val session = loginAsAdmin()

        mockMvc.perform(
            get("/api/v1/users")
                .session(session)
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/logout")
                .session(session)
        ).andExpect(status().isOk)

        mockMvc.perform(
            get("/api/v1/users")
                .session(session)
        ).andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("Non-admin user accessing /api/v1/users returns 403")
    fun nonAdminAccessingUsersReturns403() {
        val adminSession = loginAsAdmin()

        createUser(adminSession, "operator1", "password123", "OPERATOR")

        val operatorSession = TestAuth.loginActivated(mockMvc, "operator1", "password123")

        mockMvc.perform(
            get("/api/v1/users")
                .session(operatorSession)
        ).andExpect(status().isForbidden)
    }

    @Test
    @DisplayName("Admin accessing /api/v1/users returns 200")
    fun adminAccessingUsersReturns200() {
        val adminSession = loginAsAdmin()

        mockMvc.perform(
            get("/api/v1/users")
                .session(adminSession)
        ).andExpect(status().isOk)
    }

    @Test
    @DisplayName("Password change with correct current password returns 204")
    fun passwordChangeWithCorrectCurrentPasswordReturns204() {
        val adminSession = loginAsAdmin()

        createUser(adminSession, "changepw", "oldpassword1", "OPERATOR")

        val userResult = login("changepw", "oldpassword1")
        val userSession = extractSession(userResult)

        mockMvc.perform(
            put("/api/v1/users/me/password")
                .session(userSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"oldpassword1","newPassword":"newpassword1"}""")
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            post("/api/v1/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"changepw","password":"newpassword1"}""")
        ).andExpect(status().isOk)
    }

    @Test
    @DisplayName("Password change with wrong current password returns 400")
    fun passwordChangeWithWrongCurrentPasswordReturns400() {
        val adminSession = loginAsAdmin()

        createUser(adminSession, "wrongpw", "correctpass1", "OPERATOR")

        val userResult = login("wrongpw", "correctpass1")
        val userSession = extractSession(userResult)

        mockMvc.perform(
            put("/api/v1/users/me/password")
                .session(userSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"wrongcurrent","newPassword":"newpassword1"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("Deactivated user cannot log in")
    fun deactivatedUserCannotLogIn() {
        val adminSession = loginAsAdmin()

        val createResult = createUser(adminSession, "todeactivate", "password123", "OPERATOR")
        val userId = extractId(createResult.response.contentAsString)

        mockMvc.perform(
            put("/api/v1/users/$userId")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"active":false}""")
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"todeactivate","password":"password123"}""")
        ).andExpect(status().isUnauthorized)
    }

    /**
     * Proves RequestIdFilter is a container-level filter, not a member of the
     * security chain, so its @Order applies relative to the chain as a whole.
     * That plus the HIGHEST_PRECEDENCE assertion in RequestIdFilterTest is what
     * places it first; neither test observes the resolved ordering directly.
     */
    @Test
    @DisplayName("Request id filter is registered outside the security filter chain")
    fun requestIdFilterIsNotInSecurityChain() {
        val securityFilters = filterChainProxy.filterChains.flatMap { it.filters }

        assertTrue(securityFilters.isNotEmpty(), "Expected a populated security filter chain")
        assertTrue(
            securityFilters.none { it is RequestIdFilter },
            "RequestIdFilter must run ahead of the security chain, not inside it",
        )
    }

    private fun extractId(json: String): String {
        val pattern = """"id"\s*:\s*(\d+)""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
            ?: throw AssertionError("Could not extract 'id' from response: $json")
    }
}
