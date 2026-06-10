package com.example.donations.user

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateUserRequest(
    @field:NotBlank(message = "Username is required")
    val username: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, message = "Password must be at least 8 characters")
    val password: String,

    @field:NotEmpty(message = "At least one role is required")
    val roles: Set<Role>,

    val active: Boolean = true,
)

data class UpdateUserRequest(
    val username: String? = null,

    @field:Size(min = 8, message = "Password must be at least 8 characters")
    val password: String? = null,

    val roles: Set<Role>? = null,

    val active: Boolean? = null,
)

data class ChangePasswordRequest(
    @field:NotBlank(message = "Current password is required")
    val currentPassword: String,

    @field:NotBlank(message = "New password is required")
    @field:Size(min = 8, message = "New password must be at least 8 characters")
    val newPassword: String,
)

data class UserResponse(
    val id: Long,
    val username: String,
    val roles: List<String>,
    val active: Boolean,
    val mustChangePassword: Boolean,
    val createdAt: Instant?,
    val updatedAt: Instant?,
) {
    companion object {
        fun from(user: User): UserResponse = UserResponse(
            id = user.id!!,
            username = user.username,
            roles = user.roles.map { it.name },
            active = user.active,
            mustChangePassword = user.mustChangePassword,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt,
        )
    }
}
