package com.example.donations.user

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.security.core.userdetails.User as SpringUser

@Service
class AppUserDetailsService(
    private val userRepository: UserRepository,
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("User not found: $username")

        if (!user.active) {
            throw UsernameNotFoundException("User account is inactive: $username")
        }

        val authorities = user.roles.map { role ->
            SimpleGrantedAuthority("ROLE_${role.name}")
        }

        return SpringUser.builder()
            .username(user.username)
            .password(user.password)
            .authorities(authorities)
            .build()
    }
}
