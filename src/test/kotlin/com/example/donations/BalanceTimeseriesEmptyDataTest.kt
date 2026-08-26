package com.example.donations

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

/**
 * The empty-ledger contract needs a database with no donations and no expenses, which
 * `FinancialReportsTest` cannot provide — it seeds both in `@BeforeEach`. Hence its own
 * class and its own container.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Balance Timeseries Empty Data Tests")
class BalanceTimeseriesEmptyDataTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    private lateinit var treasurerSession: MockHttpSession

    @BeforeEach
    fun setUp() {
        val adminSession = TestAuth.loginAsAdmin(mockMvc)

        mockMvc.perform(
            post("/api/v1/users")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"treasurer2","password":"password123","roles":["TREASURER"]}""")
        ).andExpect(status().isCreated)

        treasurerSession = TestAuth.loginActivated(mockMvc, "treasurer2", "password123")
    }

    @Test
    @DisplayName("Balance timeseries over an empty ledger returns no periods")
    fun emptyLedgerReturnsNoPeriods() {
        val today = FixedClockConfiguration.TODAY

        mockMvc.perform(
            get("/api/v1/reports/balance/timeseries")
                .session(treasurerSession)
                .param("from", "2026-01-01")
                .param("groupBy", "MONTH")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.from").value("2026-01-01"))
            .andExpect(jsonPath("$.to").value(today.toString()))
            .andExpect(jsonPath("$.periods").isArray)
            .andExpect(jsonPath("$.periods.length()").value(0))
    }

    @Test
    @DisplayName("An inverted range is a 400 even when the ledger is empty")
    fun invertedRangeIsRejectedOnAnEmptyLedger() {
        // Whether a request is well-formed cannot depend on whether the church has
        // recorded anything yet — and no envelope should come back with from after to.
        mockMvc.perform(
            get("/api/v1/reports/balance/timeseries")
                .session(treasurerSession)
                .param("from", FixedClockConfiguration.TODAY.plusYears(1).toString())
                .param("groupBy", "MONTH")
        ).andExpect(status().isBadRequest)
    }
}
