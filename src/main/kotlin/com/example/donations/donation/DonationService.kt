package com.example.donations.donation

import com.example.donations.donor.DonorRepository
import com.example.donations.infrastructure.defaultYearRange
import com.example.donations.infrastructure.error.NotFoundException
import com.example.donations.infrastructure.events.DonationCreated
import com.example.donations.infrastructure.events.DonationUpdated
import com.example.donations.infrastructure.events.EventLogger
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class DonationService(
    private val donationRepository: DonationRepository,
    private val donorRepository: DonorRepository,
    private val eventLogger: EventLogger,
    private val clock: Clock,
) {

    fun listDonations(pageable: Pageable, from: LocalDate?, to: LocalDate?): Page<Donation> {
        val (effectiveFrom, effectiveTo) = defaultYearRange(from, to, clock)
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
                eventLogger.emit(DonationCreated(saved.id!!, saved.donor?.id, saved.amount))
                return DonationCreateResponse.savedWithWarning(saved)
            }
        }

        val donation = buildDonation(request, donor)
        val saved = donationRepository.save(donation)
        eventLogger.emit(DonationCreated(saved.id!!, saved.donor?.id, saved.amount))
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

        val saved = donationRepository.save(donation)
        eventLogger.emit(DonationUpdated(id))
        return saved
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
