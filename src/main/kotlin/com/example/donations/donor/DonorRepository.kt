package com.example.donations.donor

import org.springframework.data.jpa.repository.JpaRepository

interface DonorRepository : JpaRepository<Donor, Long> {

    fun existsByNationalId(nationalId: String): Boolean
}
