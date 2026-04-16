package com.example.donations

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@DisplayName("OpenAPI Specification Tests")
class OpenApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    @DisplayName("OpenAPI spec is accessible without authentication")
    fun openApiSpecAccessibleWithoutAuth() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.info.title").value("Church Donations API"))
            .andExpect(jsonPath("$.info.version").value("v1"))
            .andExpect(jsonPath("$.info.description").isNotEmpty)
    }

    @Test
    @DisplayName("Swagger UI is accessible without authentication")
    fun swaggerUiAccessibleWithoutAuth() {
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk)
    }

    @Test
    @DisplayName("OpenAPI spec contains all API endpoints")
    fun openApiSpecContainsAllEndpoints() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            // Auth (logout is Spring Security filter, not in OpenAPI)
            .andExpect(jsonPath("$.paths['/api/v1/login']").exists())
            // Users
            .andExpect(jsonPath("$.paths['/api/v1/users']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/users/{id}']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/users/me/password']").exists())
            // Donors
            .andExpect(jsonPath("$.paths['/api/v1/donors']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/donors/{id}']").exists())
            // Donations
            .andExpect(jsonPath("$.paths['/api/v1/donations']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/donations/{id}']").exists())
            // Expenses
            .andExpect(jsonPath("$.paths['/api/v1/expenses']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/expenses/{id}']").exists())
            // Reports
            .andExpect(jsonPath("$.paths['/api/v1/reports/donations']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/reports/expenses']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/reports/balance']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/reports/donors/{id}/statement']").exists())
    }
}
