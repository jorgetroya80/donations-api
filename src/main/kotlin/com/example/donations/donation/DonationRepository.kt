package com.example.donations.donation

import com.example.donations.donor.Donor
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

interface DonationRepository : JpaRepository<Donation, Long> {

    fun existsByDonorAndAmountAndDonationDateAndDonationType(
        donor: Donor,
        amount: BigDecimal,
        donationDate: LocalDate,
        donationType: DonationType,
    ): Boolean

    // The donor association is mapped to DTOs after the service transaction ends
    // (open-in-view is off), so it must be fetched with the donation.
    @EntityGraph(attributePaths = ["donor"])
    override fun findById(id: Long): Optional<Donation>

    @EntityGraph(attributePaths = ["donor"])
    fun findByDonationDateBetween(
        from: LocalDate,
        to: LocalDate,
        pageable: Pageable,
    ): Page<Donation>

    @Query(
        "SELECT d.donationType, SUM(d.amount) FROM Donation d " +
            "WHERE d.donationDate BETWEEN :from AND :to GROUP BY d.donationType"
    )
    fun sumByTypeAndDateBetween(from: LocalDate, to: LocalDate): List<Array<Any>>

    @Query(
        "SELECT SUM(d.amount) FROM Donation d " +
            "WHERE d.donationDate BETWEEN :from AND :to"
    )
    fun sumAmountByDateBetween(from: LocalDate, to: LocalDate): BigDecimal?

    fun findByDonorIdAndDonationDateBetween(
        donorId: Long,
        from: LocalDate,
        to: LocalDate,
    ): List<Donation>
}
