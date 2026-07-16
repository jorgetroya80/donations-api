package com.example.donations.user

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class AuthController(
    private val authService: AuthService,
) {

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<LoginResponse> = ResponseEntity.ok(authService.login(request, httpRequest))
}
