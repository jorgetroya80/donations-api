package com.example.donations.infrastructure.config

import com.example.donations.infrastructure.events.RequestIdFilter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper
import java.net.URI

/**
 * Blocks users flagged with must_change_password from everything except
 * changing their password and logging out. The flag is cached as a session
 * attribute at login and cleared on self-service password change.
 */
@Component
class PasswordChangeRequiredFilter(
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val flagged = request.getSession(false)?.getAttribute(SESSION_ATTRIBUTE) == true
        if (flagged && !isAllowedWhileFlagged(request)) {
            // Same RFC 9457 shape as GlobalExceptionHandler: this runs in the filter
            // chain, so the body is produced here (ADR-004). "code" is an extension
            // property letting clients distinguish this 403 from a role denial.
            val problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Password change required")
            problem.title = HttpStatus.FORBIDDEN.reasonPhrase
            problem.instance = URI.create(request.requestURI)
            problem.setProperty("code", "PASSWORD_CHANGE_REQUIRED")
            MDC.get(RequestIdFilter.REQUEST_ID)?.let { problem.setProperty("requestId", it) }
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.characterEncoding = Charsets.UTF_8.name()
            response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
            response.writer.write(objectMapper.writeValueAsString(problem))
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
