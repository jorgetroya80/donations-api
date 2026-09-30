package com.example.donations.infrastructure.error

import org.springframework.data.repository.CrudRepository

fun <T : Any> CrudRepository<T, Long>.getOrThrow(id: Long, label: String): T =
    findById(id).orElseThrow { NotFoundException("$label not found with id: $id") }
