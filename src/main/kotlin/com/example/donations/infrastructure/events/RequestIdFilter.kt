package com.example.donations.infrastructure.events

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Puts a correlation id in MDC for the life of every request, so all events from
 * one request can be recovered from a single id (ADR-005). Ordered ahead of the
 * security chain so requests rejected before authentication also carry one.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        MDC.put(REQUEST_ID, UUID.randomUUID().toString())
        try {
            filterChain.doFilter(request, response)
        } finally {
            // Remove only our own key: MDC is shared with anything else on this
            // pooled thread.
            MDC.remove(REQUEST_ID)
        }
    }

    companion object {
        const val REQUEST_ID = "requestId"
    }
}
