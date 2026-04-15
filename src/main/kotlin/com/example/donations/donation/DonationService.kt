package com.example.donations.donation

import com.example.donations.donor.DonorRepository
import com.example.donations.infrastructure.error.NotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.Year

@Service
@Transactional(readOnly = true)
class DonationService(
    private val donationRepository: DonationRepository,
    private val donorRepository: DonorRepository,
) {

    fun listDonations(pageable: Pageable, from: LocalDate?, to: LocalDate?): Page<Donation> {
        val effectiveFrom = from ?: LocalDate.of(Year.now().value, 1, 1)
        val effectiveTo = to ?: LocalDate.of(Year.now().value, 12, 31)
        return donationRepository.findByDonationDateBetween(effectiveFrom, effectiveTo, pageable)
    }

    fun getDonation(id: Long): Donation {
        return donationRepository.findById(id)
            .orElseThrow { NotFoundException("Donation not found with id: $id") }
    }

    @Transactional
    fun createDonation(request: CreateDonationRequest): DonationCreateResponse {
        val donor = request.donorId?.let { donorId ->
            donorRepository.findById(donorId)
                .orElseThrow { NotFoundException("Donor not found with id: $donorId") }
        }

        if (donor != null) {
            val isDuplicate = donationRepository.existsByDonorAndAmountAndDonationDateAndDonationType(
                donor = donor,
                amount = request.amount!!,
                donationDate = request.donationDate!!,
                donationType = request.donationType!!,
            )

            if (isDuplicate && !request.confirmDuplicate) {
                return DonationCreateResponse.duplicateDetected()
            }

            if (isDuplicate && request.confirmDuplicate) {
                val donation = buildDonation(request, donor)
                val saved = donationRepository.save(donation)
                return DonationCreateResponse.savedWithWarning(saved)
            }
        }

        val donation = buildDonation(request, donor)
        val saved = donationRepository.save(donation)
        return DonationCreateResponse.saved(saved)
    }

    @Transactional
    fun updateDonation(id: Long, request: UpdateDonationRequest): Donation {
        val donation = donationRepository.findById(id)
            .orElseThrow { NotFoundException("Donation not found with id: $id") }

        request.amount?.let { donation.amount = it }
        request.donationDate?.let { donation.donationDate = it }
        request.donationType?.let { donation.donationType = it }
        request.paymentMethod?.let { donation.paymentMethod = it }
        request.notes?.let { donation.notes = it }

        if (request.donorId != null) {
            val donor = donorRepository.findById(request.donorId)
                .orElseThrow { NotFoundException("Donor not found with id: ${request.donorId}") }
            donation.donor = donor
        }

        return donationRepository.save(donation)
    }

    private fun buildDonation(request: CreateDonationRequest, donor: com.example.donations.donor.Donor?): Donation {
        return Donation(
            amount = request.amount!!,
            donationDate = request.donationDate!!,
            donationType = request.donationType!!,
            paymentMethod = request.paymentMethod!!,
            donor = donor,
            notes = request.notes,
        )
    }
}
