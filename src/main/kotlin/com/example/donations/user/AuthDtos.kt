package com.example.donations.user

import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:NotBlank(message = "Username is required")
    val username: String,

    @field:NotBlank(message = "Password is required")
    val password: String,
)

data class LoginResponse(
    val username: String,
    val roles: List<String>,
    val mustChangePassword: Boolean,
)
