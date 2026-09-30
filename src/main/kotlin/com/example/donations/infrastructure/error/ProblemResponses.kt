package com.example.donations.infrastructure.error

import com.example.donations.infrastructure.events.RequestIdFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import tools.jackson.databind.ObjectMapper
import java.net.URI

/**
 * Writes an RFC 9457 problem body from the filter chain, where
 * GlobalExceptionHandler is not reached (ADR-004). [extras] are added in order,
 * followed by requestId.
 */
fun writeProblem(
    request: HttpServletRequest,
    response: HttpServletResponse,
    objectMapper: ObjectMapper,
    status: HttpStatus,
    detail: String,
    extras: Map<String, Any> = emptyMap(),
) {
    val problem = ProblemDetail.forStatusAndDetail(status, detail)
    problem.title = status.reasonPhrase
    problem.instance = URI.create(request.requestURI)
    extras.forEach { (key, value) -> problem.setProperty(key, value) }
    MDC.get(RequestIdFilter.REQUEST_ID)?.let { problem.setProperty("requestId", it) }
    response.status = status.value()
    response.characterEncoding = Charsets.UTF_8.name()
    response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
    response.writer.write(objectMapper.writeValueAsString(problem))
    response.writer.flush()
}
