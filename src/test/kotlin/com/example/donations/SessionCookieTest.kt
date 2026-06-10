package com.example.donations

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.core.env.Environment
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@DisplayName("Session Cookie Tests")
class SessionCookieTest {

    @Autowired
    private lateinit var environment: Environment

    private val client: HttpClient = HttpClient.newHttpClient()

    private fun baseUrl() = "http://localhost:${environment.getProperty("local.server.port")}"

    @Test
    @DisplayName("Login sets session cookie with HttpOnly and SameSite=Lax")
    fun loginCookieHasSecurityAttributes() {
        val response = client.send(
            HttpRequest.newBuilder(URI.create("${baseUrl()}/api/v1/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"username":"admin","password":"admin"}"""))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        assertEquals(200, response.statusCode())
        val setCookie = response.headers().allValues("Set-Cookie").firstOrNull { it.startsWith("JSESSIONID") }
            ?: throw AssertionError("No JSESSIONID Set-Cookie on login")
        assertTrue(setCookie.contains("HttpOnly", ignoreCase = true), "Expected HttpOnly in: $setCookie")
        assertTrue(setCookie.contains("SameSite=Lax", ignoreCase = true), "Expected SameSite=Lax in: $setCookie")
    }

    @Test
    @DisplayName("Anonymous request to public endpoint creates no session cookie")
    fun anonymousRequestCreatesNoSessionCookie() {
        val response = client.send(
            HttpRequest.newBuilder(URI.create("${baseUrl()}/actuator/health")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        assertEquals(200, response.statusCode())
        assertTrue(
            response.headers().allValues("Set-Cookie").none { it.startsWith("JSESSIONID") },
            "Anonymous request must not create a session cookie",
        )
    }
}
