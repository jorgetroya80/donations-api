package com.example.donations.donor

import com.example.donations.donor.validation.ValidNationalId
import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class CreateDonorRequest(
    @field:NotBlank(message = "Full name is required")
    val fullName: String,

    @field:NotBlank(message = "National ID is required")
    @field:ValidNationalId
    val nationalId: String,

    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
)

data class UpdateDonorRequest(
    val fullName: String? = null,

    @field:ValidNationalId
    val nationalId: String? = null,

    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val active: Boolean? = null,
)

data class DonorResponse(
    val id: Long,
    val fullName: String,
    val nationalId: String,
    val email: String?,
    val phone: String?,
    val address: String?,
    val active: Boolean,
    val createdAt: Instant?,
    val updatedAt: Instant?,
) {
    companion object {
        fun from(donor: Donor): DonorResponse = DonorResponse(
            id = donor.id!!,
            fullName = donor.fullName,
            nationalId = donor.nationalId,
            email = donor.email,
            phone = donor.phone,
            address = donor.address,
            active = donor.active,
            createdAt = donor.createdAt,
            updatedAt = donor.updatedAt,
        )
    }
}
