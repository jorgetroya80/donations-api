package com.example.donations.infrastructure.session

import jakarta.servlet.http.HttpSession
import jakarta.servlet.http.HttpSessionEvent
import jakarta.servlet.http.HttpSessionListener
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

/**
 * Tracks live sessions per user so password changes can revoke them.
 * Spring Security's SessionRegistry is not used because it is only populated
 * by filter-chain authentication, which the manual login in AuthController
 * bypasses. In-memory, single-instance — same constraint as LoginAttemptService.
 */
@Component
class UserSessionTracker : HttpSessionListener {

    private val sessionsByUser = ConcurrentHashMap<String, MutableSet<HttpSession>>()
    private val usersBySessionId = ConcurrentHashMap<String, String>()

    fun register(username: String, session: HttpSession) {
        usersBySessionId[session.id] = username
        sessionsByUser.computeIfAbsent(username) { ConcurrentHashMap.newKeySet() }.add(session)
    }

    fun invalidateAll(username: String, exceptSessionId: String? = null) {
        sessionsByUser[username]
            ?.filter { it.id != exceptSessionId }
            ?.forEach { session ->
                // Already-invalidated sessions throw IllegalStateException
                runCatching { session.invalidate() }
            }
    }

    override fun sessionDestroyed(se: HttpSessionEvent) {
        val username = usersBySessionId.remove(se.session.id) ?: return
        sessionsByUser[username]?.removeIf { it.id == se.session.id }
    }
}
