package com.example.donations.donor

import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/donors")
@PreAuthorize("hasAnyRole('OPERATOR', 'TREASURER')")
class DonorController(
    private val donorService: DonorService,
) {

    @GetMapping
    fun listDonors(pageable: Pageable): Page<DonorResponse> =
        donorService.listDonors(pageable).map { DonorResponse.from(it) }

    @GetMapping("/{id}")
    fun getDonor(@PathVariable id: Long): DonorResponse =
        DonorResponse.from(donorService.getDonor(id))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createDonor(@Valid @RequestBody request: CreateDonorRequest): DonorResponse =
        DonorResponse.from(donorService.createDonor(request))

    @PutMapping("/{id}")
    fun updateDonor(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateDonorRequest,
    ): DonorResponse =
        DonorResponse.from(donorService.updateDonor(id, request))
}
