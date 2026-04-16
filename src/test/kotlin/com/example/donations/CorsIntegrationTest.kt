package com.example.donations

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@TestPropertySource(properties = ["app.cors.enabled=true"])
@DisplayName("CORS Integration Tests")
class CorsIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    private val allowedOrigin = "http://localhost:5173"
    private val disallowedOrigin = "http://evil.com"

    @Test
    @DisplayName("Preflight OPTIONS from allowed origin returns CORS headers")
    fun preflightFromAllowedOrigin() {
        mockMvc.perform(
            options("/api/v1/login")
                .header(HttpHeaders.ORIGIN, allowedOrigin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
        )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowedOrigin))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
            .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
    }

    @Test
    @DisplayName("Preflight OPTIONS from disallowed origin rejected")
    fun preflightFromDisallowedOrigin() {
        mockMvc.perform(
            options("/api/v1/login")
                .header(HttpHeaders.ORIGIN, disallowedOrigin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
        )
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
    }

    @Test
    @DisplayName("Simple GET from allowed origin includes CORS headers")
    fun simpleGetFromAllowedOrigin() {
        mockMvc.perform(
            get("/actuator/health")
                .header(HttpHeaders.ORIGIN, allowedOrigin)
        )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowedOrigin))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
    }

    @Test
    @DisplayName("Actuator health endpoint accessible without auth")
    fun actuatorHealthAccessible() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
    }
}
