package com.example.donations.user

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface UserRepository : JpaRepository<User, Long> {

    fun findByUsername(username: String): User?

    fun existsByUsername(username: String): Boolean

    @Query("select u.id from User u where u.username = :username")
    fun findIdByUsername(username: String): Long?
}
