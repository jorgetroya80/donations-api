package com.example.donations.user

import com.example.donations.infrastructure.audit.AuditableEntity
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import org.hibernate.annotations.BatchSize

@Entity
@Table(name = "users")
open class User(

    @Column(name = "username", nullable = false, unique = true)
    open var username: String = "",

    @Column(name = "password", nullable = false)
    open var password: String = "",

    @Column(name = "active", nullable = false)
    open var active: Boolean = true,

    @Column(name = "must_change_password", nullable = false)
    open var mustChangePassword: Boolean = false,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = [JoinColumn(name = "user_id")])
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    @BatchSize(size = 100)
    open var roles: Set<Role> = emptySet(),
) : AuditableEntity()
