package com.example.donations.infrastructure.config

import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.time.Instant

@Configuration
@EnableMethodSecurity
class SecurityConfig {

    @Value("\${app.cors.enabled:false}")
    private var corsEnabled: Boolean = false

    @Value("\${app.cors.allowed-origins}")
    private lateinit var corsAllowedOrigins: String

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .csrf { it.disable() }
        .apply { if (corsEnabled) cors(Customizer.withDefaults()) else cors { it.disable() } }
        .authorizeHttpRequests { auth ->
            auth
                .requestMatchers("/api/v1/login").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
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
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOrigins = corsAllowedOrigins.split(",").map { it.trim() }
        config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("*")
        config.allowCredentials = true
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager
}
