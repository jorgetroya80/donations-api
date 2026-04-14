package com.example.donations.infrastructure.audit

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Optional

@Configuration
@EnableJpaAuditing
class AuditorAwareConfig {

    @Bean
    fun auditorAware(): AuditorAware<String> = AuditorAware {
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.name?.takeIf { authentication.isAuthenticated }
        Optional.of(principal ?: "system")
    }
}
