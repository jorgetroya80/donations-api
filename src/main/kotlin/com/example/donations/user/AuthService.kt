package com.example.donations.user

import com.example.donations.infrastructure.config.PasswordChangeRequiredFilter
import com.example.donations.infrastructure.events.EventLogger
import com.example.donations.infrastructure.events.LoginSucceeded
import com.example.donations.infrastructure.session.UserSessionTracker
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.LockedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val authenticationManager: AuthenticationManager,
    private val loginAttemptService: LoginAttemptService,
    private val userRepository: UserRepository,
    private val userSessionTracker: UserSessionTracker,
    private val eventLogger: EventLogger,
) {

    private val log = LoggerFactory.getLogger(AuthService::class.java)

    fun login(request: LoginRequest, httpRequest: HttpServletRequest): LoginResponse {
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
        eventLogger.emit(LoginSucceeded(request.username))
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
        userSessionTracker.register(request.username, session)
        return LoginResponse(username = request.username, roles = roles, mustChangePassword = mustChangePassword)
    }
}
