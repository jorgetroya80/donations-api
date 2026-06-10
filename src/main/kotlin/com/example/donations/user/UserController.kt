package com.example.donations.user

import com.example.donations.infrastructure.config.PasswordChangeRequiredFilter
import com.example.donations.infrastructure.session.UserSessionTracker
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
    private val userSessionTracker: UserSessionTracker,
) {

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun listUsers(pageable: Pageable): Page<UserResponse> =
        userService.listUsers(pageable).map(UserResponse::from)

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun getUser(@PathVariable id: Long): UserResponse =
        UserResponse.from(userService.getUser(id))

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun createUser(@Valid @RequestBody request: CreateUserRequest): ResponseEntity<UserResponse> {
        val user = userService.createUser(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user))
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun updateUser(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateUserRequest,
    ): UserResponse {
        val user = userService.updateUser(id, request)
        if (request.password != null) {
            // Admin reset a password: revoke every live session of the target user
            userSessionTracker.invalidateAll(user.username)
        }
        return UserResponse.from(user)
    }

    @PutMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    fun changeOwnPassword(
        @Valid @RequestBody request: ChangePasswordRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<Void> {
        val username = SecurityContextHolder.getContext().authentication?.name
            ?: throw IllegalStateException("No authenticated user")
        userService.changeOwnPassword(username, request.currentPassword, request.newPassword)
        val session = httpRequest.getSession(false)
        session?.setAttribute(PasswordChangeRequiredFilter.SESSION_ATTRIBUTE, false)
        userSessionTracker.invalidateAll(username, exceptSessionId = session?.id)
        return ResponseEntity.noContent().build()
    }
}
