package com.example.donations.donation

import com.example.donations.donor.Donor
import com.example.donations.donor.DonorRepository
import com.example.donations.infrastructure.defaultYearRange
import com.example.donations.infrastructure.error.getOrThrow
import com.example.donations.infrastructure.events.DonationCreated
import com.example.donations.infrastructure.events.DonationUpdated
import com.example.donations.infrastructure.events.EventLogger
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class DonationService(
    private val donationRepository: DonationRepository,
    private val donorRepository: DonorRepository,
    private val eventLogger: EventLogger,
) {

    fun listDonations(pageable: Pageable, from: LocalDate?, to: LocalDate?): Page<Donation> {
        val (effectiveFrom, effectiveTo) = defaultYearRange(from, to)
        return donationRepository.findByDonationDateBetween(effectiveFrom, effectiveTo, pageable)
    }

    fun getDonation(id: Long): Donation = donationRepository.getOrThrow(id, "Donation")

    @Transactional
    fun createDonation(request: CreateDonationRequest): DonationCreateResponse {
        val donor = request.donorId?.let { donorId ->
            donorRepository.getOrThrow(donorId, "Donor")
        }

        val isDuplicate = donor != null && donationRepository.existsByDonorAndAmountAndDonationDateAndDonationType(
            donor = donor,
            amount = request.amount!!,
            donationDate = request.donationDate!!,
            donationType = request.donationType!!,
        )

        if (isDuplicate && !request.confirmDuplicate) {
            return DonationCreateResponse.duplicateDetected()
        }

        val saved = donationRepository.save(buildDonation(request, donor))
        eventLogger.emit(DonationCreated(saved.id!!, saved.donor?.id, saved.amount))
        return if (isDuplicate) DonationCreateResponse.savedWithWarning(saved) else DonationCreateResponse.saved(saved)
    }

    @Transactional
    fun updateDonation(id: Long, request: UpdateDonationRequest): Donation {
        val donation = donationRepository.getOrThrow(id, "Donation")

        request.amount?.let { donation.amount = it }
        request.donationDate?.let { donation.donationDate = it }
        request.donationType?.let { donation.donationType = it }
        request.paymentMethod?.let { donation.paymentMethod = it }
        request.notes?.let { donation.notes = it }

        if (request.donorId != null) {
            val donor = donorRepository.getOrThrow(request.donorId, "Donor")
            donation.donor = donor
        }

        val saved = donationRepository.save(donation)
        eventLogger.emit(DonationUpdated(id))
        return saved
    }

    private fun buildDonation(request: CreateDonationRequest, donor: Donor?): Donation {
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
