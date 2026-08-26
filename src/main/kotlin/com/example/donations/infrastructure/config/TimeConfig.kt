package com.example.donations.infrastructure.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

/**
 * "Today" is a civil-calendar question, so it must be answered in the church's zone, not the
 * JVM default. The deploy target runs UTC while the church is Europe/Madrid, which puts the
 * server one or two hours behind local midnight: without an explicit zone, a donation recorded
 * early on a local morning is clamped out of the reports as if it were in the future, and the
 * New Year rollover misfiles a whole day into the wrong year.
 */
@Configuration
class TimeConfig {

    @Bean
    fun clock(@Value("\${app.timezone}") zone: String): Clock = Clock.system(ZoneId.of(zone))
}
