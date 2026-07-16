package com.example.donations.infrastructure.error

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

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
    fun handleAccessDenied(ex: AccessDeniedException): ProblemDetail =
        problem(HttpStatus.FORBIDDEN, "Access denied")

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(ex: AuthenticationException): ProblemDetail =
        problem(HttpStatus.UNAUTHORIZED, "Authentication required")

    @ExceptionHandler(NotFoundException::class, NoSuchElementException::class)
    fun handleNotFound(ex: RuntimeException): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, ex.message ?: "Resource not found")

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException): ProblemDetail =
        problem(HttpStatus.CONFLICT, ex.message ?: "Conflict")

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, ex.message ?: "Bad request")

    @ExceptionHandler(Exception::class)
    fun handleAll(ex: Exception): ProblemDetail {
        log.error("Unexpected error", ex)
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
        return problemDetail
    }
}
