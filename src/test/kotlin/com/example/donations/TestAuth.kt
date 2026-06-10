package com.example.donations

import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

/**
 * Login helpers that deal with the must-change-password enforcement: newly
 * provisioned users (and the seeded admin) are blocked from business endpoints
 * until they set their own password.
 */
object TestAuth {

    const val ADMIN_TEST_PASSWORD = "admin-test-12345"

    fun login(mockMvc: MockMvc, username: String, password: String): MockHttpSession {
        val result = mockMvc.perform(
            post("/api/v1/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"$username","password":"$password"}""")
        ).andReturn()
        check(result.response.status == 200) { "Login failed for '$username': ${result.response.status}" }
        return result.request.getSession(false) as? MockHttpSession
            ?: throw AssertionError("No session created after login for '$username'")
    }

    /** Logs in and clears the must-change-password flag by re-setting the same password. */
    fun loginActivated(mockMvc: MockMvc, username: String, password: String): MockHttpSession {
        val session = login(mockMvc, username, password)
        changePassword(mockMvc, session, current = password, new = password)
        return session
    }

    /** Logs in the seeded admin and rotates its default password to [ADMIN_TEST_PASSWORD]. */
    fun loginAsAdmin(mockMvc: MockMvc): MockHttpSession {
        val session = login(mockMvc, "admin", "admin")
        changePassword(mockMvc, session, current = "admin", new = ADMIN_TEST_PASSWORD)
        return session
    }

    private fun changePassword(mockMvc: MockMvc, session: MockHttpSession, current: String, new: String) {
        val status = mockMvc.perform(
            put("/api/v1/users/me/password")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"$current","newPassword":"$new"}""")
        ).andReturn().response.status
        check(status == 204) { "Password change failed: $status" }
    }
}
