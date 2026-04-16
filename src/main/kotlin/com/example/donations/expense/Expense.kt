package com.example.donations.expense

import com.example.donations.donation.PaymentMethod
import com.example.donations.infrastructure.audit.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "expenses")
class Expense(

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    var amount: BigDecimal,

    @Column(name = "expense_date", nullable = false)
    var expenseDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    var category: ExpenseCategory,

    @Column(name = "description", nullable = false)
    var description: String,

    @Column(name = "vendor")
    var vendor: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 50)
    var paymentMethod: PaymentMethod,

) : AuditableEntity()
