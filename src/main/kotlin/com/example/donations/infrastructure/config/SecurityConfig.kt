package com.example.donations.infrastructure.config

import com.example.donations.infrastructure.events.RequestIdFilter
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import tools.jackson.databind.ObjectMapper
import java.net.URI

@Configuration
@EnableMethodSecurity
class SecurityConfig(
    @param:Value("\${app.cors.enabled:false}") private val corsEnabled: Boolean,
    @param:Value("\${app.cors.allowed-origins}") private val corsAllowedOrigins: String,
    @param:Value("\${springdoc.api-docs.enabled:true}") private val apiDocsEnabled: Boolean,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity, objectMapper: ObjectMapper): SecurityFilterChain = http
        .csrf { it.disable() }
        .apply { if (corsEnabled) cors(Customizer.withDefaults()) else cors { it.disable() } }
        .authorizeHttpRequests { auth ->
            auth
                .requestMatchers("/api/v1/login").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/liveness").permitAll()
            // Swagger routes are only public while springdoc serves them (disabled in prod)
            if (apiDocsEnabled) {
                auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
            }
            auth.anyRequest().authenticated()
        }
        .formLogin { it.disable() }
        .httpBasic { it.disable() }
        .exceptionHandling { exceptions ->
            // Same RFC 9457 shape as GlobalExceptionHandler: filter-chain 401s never
            // reach the @RestControllerAdvice, so the body is produced here (ADR-004).
            exceptions.authenticationEntryPoint { request, response, _ ->
                val problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication required")
                problem.title = HttpStatus.UNAUTHORIZED.reasonPhrase
                problem.instance = URI.create(request.requestURI)
                MDC.get(RequestIdFilter.REQUEST_ID)?.let { problem.setProperty("requestId", it) }
                response.status = HttpServletResponse.SC_UNAUTHORIZED
                response.characterEncoding = Charsets.UTF_8.name()
                response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
                response.writer.write(objectMapper.writeValueAsString(problem))
                response.writer.flush()
            }
        }
        .logout { logout ->
            logout
                .logoutUrl("/api/v1/logout")
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler(HttpStatusReturningLogoutSuccessHandler())
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
