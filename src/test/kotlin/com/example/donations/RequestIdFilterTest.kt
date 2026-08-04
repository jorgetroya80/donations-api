package com.example.donations

import com.example.donations.infrastructure.events.RequestIdFilter
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("Request Id Filter Tests")
class RequestIdFilterTest {

    private val filter = RequestIdFilter()

    private fun capturingChain(seen: MutableList<String?>): FilterChain =
        FilterChain { _, _ -> seen.add(MDC.get(RequestIdFilter.REQUEST_ID)) }

    private fun runFilter(chain: FilterChain) {
        filter.doFilter(MockHttpServletRequest(), MockHttpServletResponse(), chain)
    }

    @Test
    @DisplayName("Request id is in MDC while the chain executes")
    fun idPresentDuringChain() {
        val seen = mutableListOf<String?>()
        runFilter(capturingChain(seen))

        val id = seen.single()
        assertNotNull(id)
        assertTrue(id.isNotBlank())
    }

    @Test
    @DisplayName("Request id is removed after the request completes")
    fun idRemovedAfterRequest() {
        runFilter(FilterChain { _, _ -> })
        assertNull(MDC.get(RequestIdFilter.REQUEST_ID))
    }

    @Test
    @DisplayName("Request id is removed even when the chain throws")
    fun idRemovedWhenChainThrows() {
        val chain = FilterChain { _, _ -> throw IllegalStateException("boom") }

        val ex = assertFailsWith<IllegalStateException> { runFilter(chain) }
        assertEquals("boom", ex.message)
        assertNull(MDC.get(RequestIdFilter.REQUEST_ID))
    }

    @Test
    @DisplayName("Each request gets its own id")
    fun idDiffersBetweenRequests() {
        val seen = mutableListOf<String?>()
        runFilter(capturingChain(seen))
        runFilter(capturingChain(seen))

        assertEquals(2, seen.toSet().size)
    }

    @Test
    @DisplayName("Filter is ordered ahead of the security chain")
    fun orderedFirst() {
        val order = RequestIdFilter::class.java.getAnnotation(Order::class.java)
        assertNotNull(order)
        assertEquals(Ordered.HIGHEST_PRECEDENCE, order.value)
    }
}
