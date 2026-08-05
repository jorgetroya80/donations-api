package com.example.donations.user

import com.example.donations.infrastructure.error.NotFoundException
import com.example.donations.infrastructure.events.AdminAction
import com.example.donations.infrastructure.events.AdminActionType
import com.example.donations.infrastructure.events.AuthorizationChanged
import com.example.donations.infrastructure.events.EventLogger
import com.example.donations.infrastructure.events.PasswordChangeFailed
import com.example.donations.infrastructure.events.PasswordChanged
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val eventLogger: EventLogger,
) {

    @Transactional(readOnly = true)
    fun listUsers(pageable: Pageable): Page<User> = userRepository.findAll(pageable)

    @Transactional(readOnly = true)
    fun getUser(id: Long): User =
        userRepository.findById(id).orElseThrow { NotFoundException("User not found with id: $id") }

    @Transactional
    fun createUser(request: CreateUserRequest): User {
        if (userRepository.existsByUsername(request.username)) {
            throw IllegalStateException("Username '${request.username}' is already taken")
        }

        val user = User(
            username = request.username,
            password = passwordEncoder.encode(request.password)!!,
            active = request.active,
            // Admin-provisioned passwords are provisional until the user sets their own
            mustChangePassword = true,
            roles = request.roles,
        )
        val saved = userRepository.save(user)
        eventLogger.emit(AdminAction(actingUser(), AdminActionType.USER_CREATE, saved.id!!))
        return saved
    }

    @Transactional
    fun updateUser(id: Long, request: UpdateUserRequest): User {
        val user = getUser(id)
        val previousRoles = user.roles

        request.username?.let { newUsername ->
            if (newUsername != user.username && userRepository.existsByUsername(newUsername)) {
                throw IllegalStateException("Username '$newUsername' is already taken")
            }
            user.username = newUsername
        }

        request.password?.let { newPassword ->
            user.password = passwordEncoder.encode(newPassword)!!
            user.mustChangePassword = true
        }

        request.roles?.let { newRoles ->
            user.roles = newRoles
        }

        request.active?.let { newActive ->
            user.active = newActive
        }

        val saved = userRepository.save(user)
        val action = if (request.password != null) AdminActionType.PASSWORD_RESET else AdminActionType.USER_UPDATE
        // Fires unconditionally, including for a PUT that changes nothing: an
        // admin issuing a pointless write is still an admin action, and deciding
        // otherwise would mean teaching this code what "no change" means.
        eventLogger.emit(AdminAction(actingUser(), action, id))
        if (user.roles != previousRoles) {
            eventLogger.emit(
                AuthorizationChanged(user.username, describe(previousRoles), describe(user.roles), id),
            )
        }
        return saved
    }

    @Transactional
    fun changeOwnPassword(username: String, currentPassword: String, newPassword: String) {
        val user = userRepository.findByUsername(username)
            ?: throw NotFoundException("User not found: $username")

        if (!passwordEncoder.matches(currentPassword, user.password)) {
            eventLogger.emit(PasswordChangeFailed(username))
            throw IllegalArgumentException("Current password is incorrect")
        }

        user.password = passwordEncoder.encode(newPassword)!!
        user.mustChangePassword = false
        userRepository.save(user)
        eventLogger.emit(PasswordChanged(username))
    }

    private fun actingUser(): String =
        SecurityContextHolder.getContext().authentication?.name ?: "system"

    private fun describe(roles: Set<Role>): String = roles.map { it.name }.sorted().joinToString(",")
}
