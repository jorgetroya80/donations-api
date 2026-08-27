package com.example.donations.infrastructure.error

import com.example.donations.infrastructure.events.AuthorizationFailed
import com.example.donations.infrastructure.events.EventLogger
import com.example.donations.infrastructure.events.RequestIdFilter
import com.example.donations.infrastructure.events.UnexpectedError
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler(
    private val eventLogger: EventLogger,
) {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ProblemDetail {
        val fields = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid") }
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", fields)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ProblemDetail {
        val fields = ex.constraintViolations.associate { violation ->
            val path = violation.propertyPath.toString()
            val field = path.substringAfterLast('.')
            field to (violation.message ?: "invalid")
        }
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", fields)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(ex: HttpMessageNotReadableException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, "Malformed request body")

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException, request: HttpServletRequest): ProblemDetail {
        // Unauthenticated callers are rejected with a 401 before reaching a
        // handler, so the fallback is all but unreachable; it uses Spring's own
        // principal name so the value is still correct if it ever fires.
        val userid = SecurityContextHolder.getContext().authentication?.name
            ?: EventLogger.ANONYMOUS_PRINCIPAL
        eventLogger.emit(AuthorizationFailed(userid, request.requestURI))
        return problem(HttpStatus.FORBIDDEN, "Access denied")
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(ex: AuthenticationException): ProblemDetail =
        problem(HttpStatus.UNAUTHORIZED, "Authentication required")

    @ExceptionHandler(NotFoundException::class, NoSuchElementException::class)
    fun handleNotFound(ex: RuntimeException): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, ex.message ?: "Resource not found")

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException): ProblemDetail =
        problem(HttpStatus.CONFLICT, ex.message ?: "Conflict")

    // Spring's own request-binding failures — a missing required query param, an
    // unparseable date, an unknown enum value. They are caller errors, but they are
    // not IllegalArgumentException, so without this the catch-all below claims them
    // and reports 500.
    //
    // Both types are named narrowly on purpose, because each has a parent that would drag a
    // fault of ours in with it, and neither is anything a caller can fix:
    //   - not ServletRequestBindingException, the parent of the missing-param case: it also
    //     covers MissingPathVariableException, which means a @PathVariable does not match its
    //     URI template — a mapping bug.
    //   - not TypeMismatchException, the parent of the mismatch case: it also covers
    //     ConversionNotSupportedException, which means no converter is registered for a handler
    //     parameter's type — a wiring bug, and a 500 in Spring's own handler too.
    // Both stay with the catch-all, which logs them and emits UnexpectedError.
    @ExceptionHandler(MissingServletRequestParameterException::class, MethodArgumentTypeMismatchException::class)
    fun handleBadRequestBinding(ex: Exception): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, "Invalid request parameter")

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, ex.message ?: "Bad request")

    @ExceptionHandler(Exception::class)
    fun handleAll(ex: Exception, request: HttpServletRequest): ProblemDetail {
        log.error("Unexpected error", ex)
        eventLogger.emit(UnexpectedError(ex.javaClass.name, request.requestURI))
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error")
    }

    private fun problem(
        status: HttpStatus,
        detail: String,
        fields: Map<String, String>? = null,
    ): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(status, detail)
        problemDetail.title = status.reasonPhrase
        if (fields != null) {
            problemDetail.setProperty("fields", fields)
        }
        // Extension property, per ADR-004's pattern: gives the caller the one
        // value needed to recover every event from their failed request.
        MDC.get(RequestIdFilter.REQUEST_ID)?.let { problemDetail.setProperty("requestId", it) }
        return problemDetail
    }
}
