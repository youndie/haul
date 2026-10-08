package io.github.youndie.haul.feature.checkout.domain

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.FieldError
import io.github.youndie.haul.feature.checkout.AddressEntry

/**
 * The address form's rules (research §5, `Address`): street, city and ZIP are required, the ZIP is five
 * digits, and nothing is longer than its field can hold. Every field at fault, in the form's order;
 * empty when the form can be saved. Leading and trailing blanks do not count.
 */
internal fun addressProblems(entry: AddressEntry): List<FieldError> =
    buildList {
        required("street", entry.street, "Enter the street address")?.let(::add)
        tooLong("street", entry.street, STREET)?.let(::add)
        tooLong("apt", entry.apt, SHORT)?.let(::add)
        required("city", entry.city, "Enter the city")?.let(::add)
        tooLong("city", entry.city, CITY)?.let(::add)
        val zip = entry.zip.trim()
        when {
            zip.isEmpty() -> add(FieldError("zip", ErrorCode.FieldRequired, ZIP_MESSAGE))
            !ZIP.matches(zip) -> add(FieldError("zip", ErrorCode.FieldInvalid, ZIP_MESSAGE))
        }
        tooLong("doorCode", entry.doorCode, SHORT)?.let(::add)
        tooLong("courierNote", entry.courierNote, NOTE)?.let(::add)
    }

private fun required(
    field: String,
    value: String,
    message: String,
): FieldError? = if (value.isBlank()) FieldError(field, ErrorCode.FieldRequired, message) else null

private fun tooLong(
    field: String,
    value: String,
    max: Int,
): FieldError? =
    if (value.trim().length > max) FieldError(field, ErrorCode.FieldInvalid, "Keep it to $max characters") else null

private val ZIP = Regex("[0-9]{5}")

/** One sentence for an empty ZIP and a wrong one (`Checkout_Validation`): what to type, not what went wrong. */
private const val ZIP_MESSAGE = "Enter a 5-digit ZIP"
private const val STREET = 100
private const val CITY = 60
private const val SHORT = 20
private const val NOTE = 200
