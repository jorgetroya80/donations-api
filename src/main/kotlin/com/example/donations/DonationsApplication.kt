package com.example.donations

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DonationsApplication

fun main(args: Array<String>) {
	runApplication<DonationsApplication>(*args)
}
