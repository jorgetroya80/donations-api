package com.example.donations.donor

import com.example.donations.infrastructure.audit.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "donors")
class Donor(

    @Column(name = "full_name", nullable = false)
    var fullName: String,

    @Column(name = "national_id", nullable = false, unique = true, length = 20)
    var nationalId: String,

    @Column(name = "email")
    var email: String? = null,

    @Column(name = "phone", length = 50)
    var phone: String? = null,

    @Column(name = "address", columnDefinition = "TEXT")
    var address: String? = null,

    @Column(name = "active", nullable = false)
    var active: Boolean = true,

) : AuditableEntity()
