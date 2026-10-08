package io.github.youndie.haul

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Every error the server answers with; the client tells cases apart by this, never by the message. */
@Serializable
public enum class ErrorCode {
    @SerialName("validation_failed")
    ValidationFailed,

    @SerialName("category_not_found")
    CategoryNotFound,

    @SerialName("product_not_found")
    ProductNotFound,

    @SerialName("unavailable")
    Unavailable,
}

/** The body of every error answer: the code, a sentence for a person, and the field when one is at fault. */
@Serializable
public data class ErrorBody(
    val code: ErrorCode,
    val message: String,
    val field: String? = null,
)
