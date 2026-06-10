package com.example.donations.user

import com.example.donations.infrastructure.config.PasswordChangeRequiredFilter
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.LockedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class LoginRequest(val username: String, val password: String)

data class LoginResponse(val username: String, val roles: List<String>, val mustChangePassword: Boolean)

@RestController
@RequestMapping("/api/v1")
class AuthController(
    private val authenticationManager: AuthenticationManager,
    private val loginAttemptService: LoginAttemptService,
    private val userRepository: UserRepository,
) {

    private val log = LoggerFactory.getLogger(AuthController::class.java)

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest, httpRequest: HttpServletRequest): ResponseEntity<LoginResponse> {
        if (loginAttemptService.isLocked(request.username)) {
            log.warn("Rejected login for locked account '{}' from {}", request.username, httpRequest.remoteAddr)
            throw LockedException("Account temporarily locked")
        }

        val authentication = try {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(request.username, request.password)
            )
        } catch (ex: AuthenticationException) {
            loginAttemptService.recordFailure(request.username)
            log.warn("Failed login for '{}' from {}", request.username, httpRequest.remoteAddr)
            throw ex
        }
        loginAttemptService.recordSuccess(request.username)
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)

        // Rotate session ID on login to prevent session fixation: manual
        // authentication bypasses Spring Security's built-in protection.
        httpRequest.getSession(false)?.invalidate()
        val session = httpRequest.getSession(true)
        session.setAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
            context
        )

        val roles = authentication.authorities
            .mapNotNull { it.authority }
            .filter { it.startsWith("ROLE_") }
            .map { it.removePrefix("ROLE_") }
        val mustChangePassword = userRepository.findByUsername(request.username)?.mustChangePassword ?: false
        session.setAttribute(PasswordChangeRequiredFilter.SESSION_ATTRIBUTE, mustChangePassword)
        return ResponseEntity.ok(
            LoginResponse(username = request.username, roles = roles, mustChangePassword = mustChangePassword)
        )
    }
}
