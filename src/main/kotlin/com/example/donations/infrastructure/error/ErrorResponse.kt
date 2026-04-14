package com.example.donations.infrastructure.error

import java.time.Instant

data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val fields: Map<String, String>? = null,
    val timestamp: Instant = Instant.now(),
)
