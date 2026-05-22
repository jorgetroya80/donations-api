package com.example.donations.donor

import com.example.donations.infrastructure.error.NotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DonorService(
    private val donorRepository: DonorRepository,
) {

    @Transactional(readOnly = true)
    fun listDonors(pageable: Pageable): Page<Donor> =
        donorRepository.findAll(pageable)

    @Transactional(readOnly = true)
    fun getDonor(id: Long): Donor =
        donorRepository.findById(id)
            .orElseThrow { NotFoundException("Donor not found with id: $id") }

    @Transactional
    fun createDonor(request: CreateDonorRequest): Donor {
        if (donorRepository.existsByNationalId(request.nationalId)) {
            throw IllegalStateException("A donor with national ID '${request.nationalId}' already exists")
        }

        val donor = Donor(
            fullName = request.fullName,
            nationalId = request.nationalId,
            email = request.email,
            phone = request.phone,
            address = request.address,
        )

        return donorRepository.save(donor)
    }

    @Transactional
    fun updateDonor(id: Long, request: UpdateDonorRequest): Donor {
        val donor = donorRepository.findById(id)
            .orElseThrow { NotFoundException("Donor not found with id: $id") }

        request.nationalId?.let { newNationalId ->
            if (newNationalId != donor.nationalId && donorRepository.existsByNationalId(newNationalId)) {
                throw IllegalStateException("A donor with national ID '$newNationalId' already exists")
            }
            donor.nationalId = newNationalId
        }

        request.fullName?.let { donor.fullName = it }
        request.email?.let { donor.email = it }
        request.phone?.let { donor.phone = it }
        request.address?.let { donor.address = it }
        request.active?.let { donor.active = it }

        return donorRepository.save(donor)
    }
}
