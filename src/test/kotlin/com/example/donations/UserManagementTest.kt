package com.example.donations

import ch.qos.logback.classic.Level
import com.example.donations.infrastructure.events.AdminAction
import org.hamcrest.Matchers.greaterThanOrEqualTo
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("User Management Tests")
class UserManagementTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private lateinit var adminSession: MockHttpSession

    @BeforeEach
    fun setUp() {
        adminSession = TestAuth.loginAsAdmin(mockMvc)
    }

    private fun createUser(
        username: String,
        password: String = "password123",
        roles: String = """["OPERATOR"]""",
    ): MvcResult {
        return mockMvc.perform(
            post("/api/v1/users")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"$username","password":"$password","roles":$roles}""")
        ).andReturn()
    }

    private fun extractId(json: String): String {
        val pattern = """"id"\s*:\s*(\d+)""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
            ?: throw AssertionError("Could not extract 'id' from response: $json")
    }

    // --- Create user ---

    @Test
    @DisplayName("Admin creates user with valid data returns 201 with id, username, roles, no password")
    fun createUserWithValidDataReturns201() {
        mockMvc.perform(
            post("/api/v1/users")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"newuser","password":"securepass1","roles":["OPERATOR"]}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.username").value("newuser"))
            .andExpect(jsonPath("$.roles").isArray)
            .andExpect(jsonPath("$.roles[0]").value("OPERATOR"))
            .andExpect(jsonPath("$.password").doesNotExist())
    }

    @Test
    @DisplayName("Admin creates user with duplicate username returns 409")
    fun createUserWithDuplicateUsernameReturns409() {
        createUser("duplicate")

        mockMvc.perform(
            post("/api/v1/users")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"duplicate","password":"securepass1","roles":["OPERATOR"]}""")
        ).andExpect(status().isConflict)
    }

    @Test
    @DisplayName("Admin creates user with blank username returns 400 with field errors")
    fun createUserWithBlankUsernameReturns400() {
        mockMvc.perform(
            post("/api/v1/users")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"","password":"securepass1","roles":["OPERATOR"]}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fields").exists())
    }

    @Test
    @DisplayName("Admin creates user with password shorter than 8 characters returns 400")
    fun createUserWithShortPasswordReturns400() {
        mockMvc.perform(
            post("/api/v1/users")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"shortpw","password":"abc","roles":["OPERATOR"]}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fields").exists())
    }

    @Test
    @DisplayName("Admin creates user with empty roles returns 400")
    fun createUserWithEmptyRolesReturns400() {
        mockMvc.perform(
            post("/api/v1/users")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"noroles","password":"securepass1","roles":[]}""")
        ).andExpect(status().isBadRequest)
    }

    // --- List users ---

    @Test
    @DisplayName("Admin lists users returns paginated response with at least admin user")
    fun listUsersReturnsPaginatedResponse() {
        mockMvc.perform(
            get("/api/v1/users")
                .session(adminSession)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.content.length()").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.content[0].username").exists())
            .andExpect(jsonPath("$.content[0].password").doesNotExist())
            .andExpect(jsonPath("$.page.totalElements").exists())
            .andExpect(jsonPath("$.page.number").exists())
            .andExpect(jsonPath("$.page.size").exists())
    }

    // --- Get user by ID ---

    @Test
    @DisplayName("Admin gets user by ID returns 200 with user data")
    fun getUserByIdReturns200() {
        val createResult = createUser("getbyid")
        val userId = extractId(createResult.response.contentAsString)

        mockMvc.perform(
            get("/api/v1/users/$userId")
                .session(adminSession)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value("getbyid"))
            .andExpect(jsonPath("$.password").doesNotExist())
    }

    @Test
    @DisplayName("Admin gets non-existent user returns 404")
    fun getNonExistentUserReturns404() {
        mockMvc.perform(
            get("/api/v1/users/999999")
                .session(adminSession)
        ).andExpect(status().isNotFound)
    }

    // --- Update user ---

    @Test
    @DisplayName("Update user with blank username returns 400 with field error")
    fun updateUserWithBlankUsernameReturns400() {
        val createResult = createUser("blankrename")
        val userId = extractId(createResult.response.contentAsString)

        mockMvc.perform(
            put("/api/v1/users/$userId")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"   "}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fields.username").exists())
    }

    @Test
    @DisplayName("Admin updates user roles returns 200 with changed roles")
    fun updateUserRolesReturns200() {
        val createResult = createUser("updateroles")
        val userId = extractId(createResult.response.contentAsString)

        mockMvc.perform(
            put("/api/v1/users/$userId")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"roles":["TREASURER"]}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.roles").isArray)
            .andExpect(jsonPath("$.roles[0]").value("TREASURER"))
    }

    @Test
    @DisplayName("Admin deactivates user returns 200 with active=false")
    fun deactivateUserReturns200() {
        val createResult = createUser("todeactivate2")
        val userId = extractId(createResult.response.contentAsString)

        mockMvc.perform(
            put("/api/v1/users/$userId")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"active":false}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.active").value(false))
    }

    @Test
    @DisplayName("Admin resets user password, user can login with new password")
    fun resetUserPasswordAllowsLoginWithNewPassword() {
        val createResult = createUser("resetpw", "oldpassword1")
        val userId = extractId(createResult.response.contentAsString)

        mockMvc.perform(
            put("/api/v1/users/$userId")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"password":"brandnewpw1"}""")
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"resetpw","password":"oldpassword1"}""")
        ).andExpect(status().isUnauthorized)

        mockMvc.perform(
            post("/api/v1/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"resetpw","password":"brandnewpw1"}""")
        ).andExpect(status().isOk)
    }

    // --- Authorization events ---

    @Test
    @DisplayName("Admin creating a user emits one authz_admin naming the created user")
    fun createUserEmitsAdminAction() {
        lateinit var createResult: MvcResult
        val events = TestEvents.capture { createResult = createUser("auditedcreate") }

        val action = events.single { it.message == "authz_admin" }
        assertEquals(Level.WARN, action.level)
        assertEquals("admin", TestEvents.fieldsOf(action)["userid"])
        assertEquals(AdminAction.USER_CREATE, TestEvents.fieldsOf(action)["action"])
        assertEquals(
            extractId(createResult.response.contentAsString).toLong(),
            TestEvents.fieldsOf(action)["targetId"],
        )
    }

    @Test
    @DisplayName("Admin resetting a password emits authz_admin distinct from an ordinary update")
    fun passwordResetEmitsDistinctAdminAction() {
        val userId = extractId(createUser("auditedreset").response.contentAsString)

        val events = TestEvents.capture {
            mockMvc.perform(
                put("/api/v1/users/$userId")
                    .session(adminSession)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"password":"brandnewpw1"}""")
            ).andExpect(status().isOk)
        }

        val action = events.single { it.message == "authz_admin" }
        assertEquals(AdminAction.PASSWORD_RESET, TestEvents.fieldsOf(action)["action"])
        assertEquals(userId.toLong(), TestEvents.fieldsOf(action)["targetId"])
    }

    @Test
    @DisplayName("Role change emits authz_change; an update leaving the role alone does not")
    fun roleChangeEmitsAuthorizationChange() {
        val userId = extractId(createUser("auditedroles").response.contentAsString)

        val changeEvents = TestEvents.capture {
            mockMvc.perform(
                put("/api/v1/users/$userId")
                    .session(adminSession)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"roles":["TREASURER"]}""")
            ).andExpect(status().isOk)
        }

        val change = changeEvents.single { it.message == "authz_change" }
        assertEquals(Level.WARN, change.level)
        assertEquals("auditedroles", TestEvents.fieldsOf(change)["userid"])
        assertEquals("OPERATOR", TestEvents.fieldsOf(change)["from"])
        assertEquals("TREASURER", TestEvents.fieldsOf(change)["to"])
        val adminAction = changeEvents.single { it.message == "authz_admin" }
        assertEquals(AdminAction.USER_UPDATE, TestEvents.fieldsOf(adminAction)["action"])
        assertEquals(userId.toLong(), TestEvents.fieldsOf(adminAction)["targetId"])

        val noChangeEvents = TestEvents.capture {
            mockMvc.perform(
                put("/api/v1/users/$userId")
                    .session(adminSession)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"roles":["TREASURER"],"active":false}""")
            ).andExpect(status().isOk)
        }

        assertTrue(noChangeEvents.none { it.message == "authz_change" })
        assertEquals(1, noChangeEvents.count { it.message == "authz_admin" })
    }
}
