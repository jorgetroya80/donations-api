package com.example.donations.donation

import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/donations")
@PreAuthorize("hasAnyRole('OPERATOR', 'TREASURER')")
class DonationController(
    private val donationService: DonationService,
) {

    @GetMapping
    fun listDonations(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        pageable: Pageable,
    ): Page<DonationResponse> {
        return donationService.listDonations(pageable, from, to)
            .map { DonationResponse.from(it) }
    }

    @GetMapping("/{id}")
    fun getDonation(@PathVariable id: Long): DonationResponse {
        return DonationResponse.from(donationService.getDonation(id))
    }

    @PostMapping
    fun createDonation(
        @Valid @RequestBody request: CreateDonationRequest,
    ): ResponseEntity<DonationCreateResponse> {
        val result = donationService.createDonation(request)
        val status = if (result.saved) HttpStatus.CREATED else HttpStatus.OK
        return ResponseEntity.status(status).body(result)
    }

    @PutMapping("/{id}")
    fun updateDonation(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateDonationRequest,
    ): DonationResponse {
        return DonationResponse.from(donationService.updateDonation(id, request))
    }
}
