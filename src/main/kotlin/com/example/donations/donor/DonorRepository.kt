package com.example.donations.donor

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface DonorRepository : JpaRepository<Donor, Long> {

    fun existsByNationalId(nationalId: String): Boolean

    @Query(
        "SELECT d FROM Donor d " +
            "WHERE LOWER(d.fullName) LIKE LOWER(CONCAT('%', :search, '%')) ESCAPE '\\' " +
            "OR LOWER(d.nationalId) LIKE LOWER(CONCAT('%', :search, '%')) ESCAPE '\\'"
    )
    fun search(search: String, pageable: Pageable): Page<Donor>
}
