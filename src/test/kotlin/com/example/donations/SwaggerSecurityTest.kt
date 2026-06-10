package com.example.donations

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    properties = [
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false",
    ],
)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@DisplayName("Swagger Security Tests (springdoc disabled, as in prod)")
class SwaggerSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    @DisplayName("API docs require authentication when springdoc is disabled")
    fun apiDocsRequireAuthWhenSpringdocDisabled() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("Swagger UI requires authentication when springdoc is disabled")
    fun swaggerUiRequiresAuthWhenSpringdocDisabled() {
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isUnauthorized)
    }
}
