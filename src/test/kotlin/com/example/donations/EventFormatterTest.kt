package com.example.donations

import com.example.donations.expense.ExpenseCategory
import com.example.donations.infrastructure.events.AccountLocked
import com.example.donations.infrastructure.events.AdminAction
import com.example.donations.infrastructure.events.AppEvent
import com.example.donations.infrastructure.events.AuthorizationChanged
import com.example.donations.infrastructure.events.AuthorizationFailed
import com.example.donations.infrastructure.events.DonationCreated
import com.example.donations.infrastructure.events.DonationUpdated
import com.example.donations.infrastructure.events.DonorCreated
import com.example.donations.infrastructure.events.DonorUpdated
import com.example.donations.infrastructure.events.EventLogger
import com.example.donations.infrastructure.events.ExpenseCreated
import com.example.donations.infrastructure.events.ExpenseUpdated
import com.example.donations.infrastructure.events.LoginFailed
import com.example.donations.infrastructure.events.LoginSucceeded
import com.example.donations.infrastructure.events.PasswordChangeFailed
import com.example.donations.infrastructure.events.PasswordChanged
import com.example.donations.infrastructure.events.RequestIdFilter
import com.example.donations.infrastructure.events.UnexpectedError
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Import
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Renders every event through the real ECS encoder, which no other test does:
 * ListAppender captures events *before* formatting, so a formatter rejection —
 * a duplicate key, an unserializable value — is invisible to the rest of the
 * suite and silently drops the line in prod. Logback swallows the failure, so
 * the "no appender error" assertion below is as important as the JSON itself.
 */
@SpringBootTest(properties = ["logging.structured.format.console=ecs"])
@Import(TestcontainersConfiguration::class)
@ExtendWith(OutputCaptureExtension::class)
@DisplayName("Event Formatter Tests")
class EventFormatterTest {

    @Autowired
    private lateinit var eventLogger: EventLogger

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    /** One instance per event type; [sampleSetCoversEveryEventType] keeps it honest. */
    private val samples: List<AppEvent> = listOf(
        LoginSucceeded("admin", "127.0.0.1"),
        LoginFailed("admin", "127.0.0.1", locked = true),
        AccountLocked("admin", "maxretries", 5),
        PasswordChanged("admin"),
        PasswordChangeFailed("admin"),
        AuthorizationFailed("operator1", "/api/v1/users"),
        AdminAction("admin", AdminAction.USER_CREATE, 42),
        AuthorizationChanged("operator1", "OPERATOR", "ADMIN"),
        // donorId null: an anonymous donation, the only null field value in the
        // vocabulary. Other events already cover non-null Long rendering.
        DonationCreated(1, null, BigDecimal("100.00")),
        DonationUpdated(1),
        DonorCreated(7),
        DonorUpdated(7),
        ExpenseCreated(3, BigDecimal("40.00"), ExpenseCategory.SUPPLIES),
        ExpenseUpdated(3),
        UnexpectedError("java.lang.IllegalStateException", "/api/v1/donors"),
    )

    // Events are emitted inside a request in production, so MDC carries a
    // correlation id. Without it here, a field colliding with the MDC key would
    // render cleanly and the collision would go unnoticed.
    @BeforeEach
    fun setRequestId() {
        MDC.put(RequestIdFilter.REQUEST_ID, "formatter-test-request-id")
    }

    @AfterEach
    fun clearRequestId() {
        MDC.remove(RequestIdFilter.REQUEST_ID)
    }

    @Test
    @DisplayName("Every event type has a sample to render")
    fun sampleSetCoversEveryEventType() {
        assertEquals(
            AppEvent::class.sealedSubclasses.toSet(),
            samples.map { it::class }.toSet(),
        )
    }

    @Test
    @DisplayName("Every event renders as valid ECS JSON carrying its declared fields")
    fun everyEventRendersAsValidEcsJson(output: CapturedOutput) {
        samples.forEach { eventLogger.emit(it) }

        val rendered = output.all.lineSequence()
            .filter { it.startsWith("{") && """"logger":"com.example.donations.events"""" in it }
            .map { objectMapper.readValue(it, Map::class.java) }
            .associateBy { it["message"] }

        samples.forEach { sample ->
            val line = rendered[sample.name] ?: throw AssertionError(
                "No rendered line for '${sample.name}'. Rendered: ${rendered.keys}",
            )
            assertEquals(sample.name, line["event"], "event key for ${sample.name}")
            assertEquals(sample.level.name, (line["log"] as Map<*, *>)["level"], "level for ${sample.name}")
            assertEquals(
                "formatter-test-request-id",
                line[RequestIdFilter.REQUEST_ID],
                "correlation id for ${sample.name}",
            )
            sample.fields.keys.forEach { field ->
                assertTrue(line.containsKey(field), "${sample.name} is missing field '$field'")
            }
        }
    }

    /**
     * Logback reports an encoder failure on its own status stream and drops the
     * line; nothing throws. Without this, the test above could pass simply by
     * finding fewer lines than it expected.
     */
    @Test
    @DisplayName("Rendering produces no appender failure")
    fun renderingProducesNoAppenderFailure(output: CapturedOutput) {
        samples.forEach { eventLogger.emit(it) }

        assertTrue(
            "failed to append" !in output.all,
            "Logback rejected an event: ${output.all.lines().filter { "failed to append" in it }}",
        )
        assertTrue(
            "ERROR in ch.qos.logback" !in output.all,
            "Logback reported an internal error: ${output.all.lines().filter { "ERROR in ch.qos.logback" in it }}",
        )
    }
}
