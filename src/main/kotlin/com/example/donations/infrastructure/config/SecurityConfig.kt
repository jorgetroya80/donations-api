package com.example.donations.infrastructure.config

import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import java.time.Instant

@Configuration
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .csrf { it.disable() }
        .authorizeHttpRequests { auth ->
            auth
                .requestMatchers("/api/v1/login").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
        }
        .sessionManagement { session ->
            session.sessionCreationPolicy(SessionCreationPolicy.ALWAYS)
        }
        .formLogin { it.disable() }
        .httpBasic { it.disable() }
        .exceptionHandling { exceptions ->
            exceptions.authenticationEntryPoint { _, response, _ ->
                response.status = HttpServletResponse.SC_UNAUTHORIZED
                response.contentType = MediaType.APPLICATION_JSON_VALUE
                response.writer.write(
                    """{"status":401,"error":"Unauthorized","message":"Authentication required","timestamp":"${Instant.now()}"}"""
                )
                response.writer.flush()
            }
        }
        .logout { logout ->
            logout
                .logoutUrl("/api/v1/logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler { _, response, _ ->
                    response.status = HttpServletResponse.SC_OK
                    response.contentType = MediaType.APPLICATION_JSON_VALUE
                    response.writer.flush()
                }
        }
        .build()

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager
}
