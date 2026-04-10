package com.example.donations

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<DonationsApplication>().with(TestcontainersConfiguration::class).run(*args)
}
