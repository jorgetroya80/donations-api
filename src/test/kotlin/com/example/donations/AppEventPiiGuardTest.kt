package com.example.donations

import com.example.donations.infrastructure.events.AppEvent
import com.example.donations.infrastructure.events.RequestIdFilter
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("App Event PII Guard Tests")
class AppEventPiiGuardTest {

    private val forbidden = setOf(
        "nationalId",
        "dni",
        "nie",
        "address",
        "email",
        "phone",
        "password",
        "sessionId",
        "donorName",
        "fullName",
        "firstName",
        "lastName",
        "iban",
    ).map { it.lowercase() }.toSet()

    // The interface's own members describe the event, not a person: "name" is the
    // event name. Only members an event type declares itself are checked.
    private val interfaceMembers = AppEvent::class.declaredMemberProperties.map { it.name }.toSet()

    @Test
    @DisplayName("No event type declares a field holding personal data")
    fun noForbiddenFields() {
        val eventTypes = allEventTypes(AppEvent::class)
        assertTrue(eventTypes.isNotEmpty(), "expected at least one event type in the hierarchy")

        eventTypes.forEach { type ->
            val declared = type.declaredMemberProperties.map { it.name } +
                (type.primaryConstructor?.parameters?.mapNotNull { it.name } ?: emptyList())
            declared.filterNot { it in interfaceMembers }.forEach { fieldName ->
                assertTrue(
                    fieldName.lowercase() !in forbidden,
                    "${type.simpleName} declares forbidden field '$fieldName'",
                )
            }
        }
    }

    /**
     * EventLogger writes "event" and "actor" itself, and RequestIdFilter puts
     * "requestId" in MDC. Spring Boot's structured formatter rejects a duplicate
     * key by dropping the entire line, so a colliding event is invisible in prod
     * while still passing a ListAppender test.
     *
     * Checked against the emitted keys, not the declared property names: a
     * property named `traceId` mapped to "requestId" collides just as fatally.
     */
    @Test
    @DisplayName("No event emits a field key that collides with a reserved log key")
    fun noReservedKeyCollisions() {
        val reserved = setOf("event", "actor", RequestIdFilter.REQUEST_ID)

        TestEvents.samples.forEach { event ->
            event.fields.keys.forEach { key ->
                assertTrue(
                    key !in reserved,
                    "${event::class.simpleName} emits '$key', which collides with a reserved log key",
                )
            }
        }
    }

    /** Keeps the shared sample list honest, so both guards see every event type. */
    @Test
    @DisplayName("The shared sample list covers every event type")
    fun samplesCoverEveryEventType() {
        assertEquals(
            allEventTypes(AppEvent::class).toSet(),
            TestEvents.samples.map { it::class }.toSet(),
        )
    }

    private fun allEventTypes(root: KClass<out AppEvent>): List<KClass<out AppEvent>> =
        root.sealedSubclasses.flatMap { listOf(it) + allEventTypes(it) }
}
