package com.example.donations.infrastructure.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Blocks users flagged with must_change_password from everything except
 * changing their password and logging out. The flag is cached as a session
 * attribute at login and cleared on self-service password change.
 */
@Component
class PasswordChangeRequiredFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val flagged = request.getSession(false)?.getAttribute(SESSION_ATTRIBUTE) == true
        if (flagged && !isAllowedWhileFlagged(request)) {
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write(
                """{"status":403,"error":"Forbidden","message":"Password change required","code":"PASSWORD_CHANGE_REQUIRED"}"""
            )
            return
        }
        filterChain.doFilter(request, response)
    }

    private fun isAllowedWhileFlagged(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return path == "/api/v1/login" ||
            path == "/api/v1/logout" ||
            (path == "/api/v1/users/me/password" && request.method == "PUT")
    }

    companion object {
        const val SESSION_ATTRIBUTE = "MUST_CHANGE_PASSWORD"
    }
}
