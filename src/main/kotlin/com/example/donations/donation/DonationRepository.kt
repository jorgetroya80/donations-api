package com.example.donations.donation

import com.example.donations.donor.Donor
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.math.BigDecimal
import java.time.LocalDate

interface DonationRepository : JpaRepository<Donation, Long> {

    fun existsByDonorAndAmountAndDonationDateAndDonationType(
        donor: Donor,
        amount: BigDecimal,
        donationDate: LocalDate,
        donationType: DonationType,
    ): Boolean

    fun findByDonationDateBetween(
        from: LocalDate,
        to: LocalDate,
        pageable: Pageable,
    ): Page<Donation>
}
