package com.example.donations.infrastructure.error

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val fields = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid") }
        return respond(HttpStatus.BAD_REQUEST, "Validation failed", fields)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ResponseEntity<ErrorResponse> {
        val fields = ex.constraintViolations.associate { violation ->
            val path = violation.propertyPath.toString()
            val field = path.substringAfterLast('.')
            field to (violation.message ?: "invalid")
        }
        return respond(HttpStatus.BAD_REQUEST, "Validation failed", fields)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.BAD_REQUEST, "Malformed request body")

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.FORBIDDEN, "Access denied")

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(ex: AuthenticationException): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.UNAUTHORIZED, "Authentication required")

    @ExceptionHandler(NotFoundException::class, NoSuchElementException::class)
    fun handleNotFound(ex: RuntimeException): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.NOT_FOUND, ex.message ?: "Resource not found")

    @ExceptionHandler(Exception::class)
    fun handleAll(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unexpected error", ex)
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error")
    }

    private fun respond(
        status: HttpStatus,
        message: String,
        fields: Map<String, String>? = null,
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(status).body(
            ErrorResponse(
                status = status.value(),
                error = status.reasonPhrase,
                message = message,
                fields = fields,
            ),
        )
}
