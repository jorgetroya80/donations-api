package com.example.donations.donor.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

class NationalIdValidator : ConstraintValidator<ValidNationalId, String?> {

    companion object {
        private const val CHECK_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE"
        private val DNI_PATTERN = Regex("^\\d{8}[A-Z]$")
        private val NIE_PATTERN = Regex("^[XYZ]\\d{7}[A-Z]$")
    }

    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true

        val normalized = value.uppercase().trim()

        return when {
            DNI_PATTERN.matches(normalized) -> {
                val number = normalized.substring(0, 8).toLong()
                val expectedLetter = CHECK_LETTERS[(number % 23).toInt()]
                normalized[8] == expectedLetter
            }
            NIE_PATTERN.matches(normalized) -> {
                val prefix = when (normalized[0]) {
                    'X' -> "0"
                    'Y' -> "1"
                    'Z' -> "2"
                    else -> return false
                }
                val number = (prefix + normalized.substring(1, 8)).toLong()
                val expectedLetter = CHECK_LETTERS[(number % 23).toInt()]
                normalized[8] == expectedLetter
            }
            else -> false
        }
    }
}
