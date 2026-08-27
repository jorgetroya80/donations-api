package com.example.donations

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * `@PastOrPresent` decides what "today" means through Bean Validation's own clock provider, which
 * is separate from the application's `Clock` bean. If the two disagree, a donation dated today in
 * the church's zone is rejected as being in the future — the exact bug the zoned clock exists to
 * prevent, on the recording side rather than the reporting side.
 *
 * The clock below is pinned to an instant where the zone genuinely matters: 13:00 UTC is already
 * the 28th in Pacific/Auckland but still the 27th in UTC *and* in Europe/Madrid, so this fails on
 * a CI container and on a Spanish laptop alike if validation falls back to the JVM default zone.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, ValidationClockZoneTest.AheadOfUtcClockConfiguration::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Validation Clock Zone Tests")
class ValidationClockZoneTest {

    @TestConfiguration(proxyBeanMethods = false)
    class AheadOfUtcClockConfiguration {

        @Bean
        @Primary
        fun aheadOfUtcClock(): Clock =
            Clock.fixed(Instant.parse("2026-08-27T13:00:00Z"), ZoneId.of("Pacific/Auckland"))
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    private lateinit var operatorSession: MockHttpSession
    private var donorId: Long = 0

    @BeforeEach
    fun setUp() {
        val adminSession = TestAuth.loginAsAdmin(mockMvc)

        mockMvc.perform(
            post("/api/v1/users")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"operator3","password":"password123","roles":["OPERATOR"]}""")
        ).andExpect(status().isCreated)

        operatorSession = TestAuth.loginActivated(mockMvc, "operator3", "password123")

        val donor = mockMvc.perform(
            post("/api/v1/donors")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fullName":"Ana Ruiz","nationalId":"12345678Z"}""")
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        donorId = Regex(""""id":(\d+)""").find(donor)!!.groupValues[1].toLong()
    }

    @Test
    @DisplayName("A donation dated today in the configured zone is accepted, not rejected as future")
    fun donationDatedTodayInConfiguredZoneIsAccepted() {
        mockMvc.perform(
            post("/api/v1/donations")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"amount":50.00,"donationDate":"2026-08-28","donationType":"OFFERING",""" +
                        """"paymentMethod":"CASH","donorId":$donorId}"""
                )
        ).andExpect(status().isCreated)
    }

    @Test
    @DisplayName("A donation dated tomorrow in the configured zone is still rejected")
    fun donationDatedTomorrowIsStillRejected() {
        mockMvc.perform(
            post("/api/v1/donations")
                .session(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"amount":50.00,"donationDate":"2026-08-29","donationType":"OFFERING",""" +
                        """"paymentMethod":"CASH","donorId":$donorId}"""
                )
        ).andExpect(status().isBadRequest)
    }
}
