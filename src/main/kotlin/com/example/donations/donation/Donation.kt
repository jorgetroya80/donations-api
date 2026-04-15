package com.example.donations.donation

import com.example.donations.donor.Donor
import com.example.donations.infrastructure.audit.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "donations")
class Donation(

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    var amount: BigDecimal,

    @Column(name = "donation_date", nullable = false)
    var donationDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(name = "donation_type", nullable = false, length = 50)
    var donationType: DonationType,

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 50)
    var paymentMethod: PaymentMethod,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donor_id")
    var donor: Donor? = null,

    @Column(name = "notes")
    var notes: String? = null,

) : AuditableEntity()
