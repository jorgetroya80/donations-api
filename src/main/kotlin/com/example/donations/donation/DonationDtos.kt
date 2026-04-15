package com.example.donations.donation

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PastOrPresent
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class CreateDonationRequest(

    @field:NotNull(message = "Amount is required")
    @field:DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    val amount: BigDecimal?,

    @field:NotNull(message = "Donation date is required")
    @field:PastOrPresent(message = "Donation date cannot be in the future")
    val donationDate: LocalDate?,

    @field:NotNull(message = "Donation type is required")
    val donationType: DonationType?,

    @field:NotNull(message = "Payment method is required")
    val paymentMethod: PaymentMethod?,

    val donorId: Long? = null,

    val notes: String? = null,

    val confirmDuplicate: Boolean = false,
)

data class UpdateDonationRequest(
    val amount: BigDecimal? = null,
    val donationDate: LocalDate? = null,
    val donationType: DonationType? = null,
    val paymentMethod: PaymentMethod? = null,
    val donorId: Long? = null,
    val notes: String? = null,
)

data class DonationResponse(
    val id: Long,
    val amount: BigDecimal,
    val donationDate: LocalDate,
    val donationType: DonationType,
    val paymentMethod: PaymentMethod,
    val donorId: Long?,
    val donorName: String?,
    val notes: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
) {
    companion object {
        fun from(donation: Donation): DonationResponse = DonationResponse(
            id = donation.id!!,
            amount = donation.amount,
            donationDate = donation.donationDate,
            donationType = donation.donationType,
            paymentMethod = donation.paymentMethod,
            donorId = donation.donor?.id,
            donorName = donation.donor?.fullName,
            notes = donation.notes,
            createdAt = donation.createdAt,
            updatedAt = donation.updatedAt,
        )
    }
}

data class DonationCreateResponse(
    val donation: DonationResponse?,
    val duplicateWarning: Boolean,
    val saved: Boolean,
) {
    companion object {
        fun saved(donation: Donation): DonationCreateResponse = DonationCreateResponse(
            donation = DonationResponse.from(donation),
            duplicateWarning = false,
            saved = true,
        )

        fun savedWithWarning(donation: Donation): DonationCreateResponse = DonationCreateResponse(
            donation = DonationResponse.from(donation),
            duplicateWarning = true,
            saved = true,
        )

        fun duplicateDetected(): DonationCreateResponse = DonationCreateResponse(
            donation = null,
            duplicateWarning = true,
            saved = false,
        )
    }
}
