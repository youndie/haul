package io.github.youndie.haul.feature.reviews

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.FieldError
import kotlinx.serialization.Serializable

// The bodies of feature-reviews' two commands (endpoint-reviews) and the rules both are held to. Where
// each is sent is the server's string, carried by the dialog's component (`ReviewForm.url`,
// `QuestionForm.url`); both answer kompot's `sequence` of `close` and `refresh` — the dialog goes and
// the page is drawn again with what was written — or an `ErrorBody`.
//
// The rules live here, once: the server refuses a form by them (`400 validation_failed`, every field at
// fault in `ErrorBody.fields`), and the client checks the same rules before it sends (the canvas's note
// on Product_ReviewDialog: «validated on the client with the server's rules»).

/** «Write a review», `POST` to `ReviewForm.url`: [rating] 1…5, [title] 1…120 characters, [body] 20…5,000. */
@Serializable
public data class ReviewEntry(
    val rating: Int = 0,
    val title: String = "",
    val body: String = "",
)

/** «Ask a question», `POST` to `QuestionForm.url`: [text] 10…1,000 characters. */
@Serializable
public data class QuestionEntry(
    val text: String = "",
)

/** feature-reviews' limits. Lengths count the text without its leading and trailing blanks. */
public object ReviewRules {
    public const val RATING_MIN: Int = 1
    public const val RATING_MAX: Int = 5
    public const val TITLE_MAX: Int = 120
    public const val BODY_MIN: Int = 20
    public const val BODY_MAX: Int = 5_000
    public const val QUESTION_MIN: Int = 10
    public const val QUESTION_MAX: Int = 1_000
}

/** Every field of [entry] at fault, in the form's order; empty when it can be posted. */
public fun reviewProblems(entry: ReviewEntry): List<FieldError> =
    buildList {
        if (entry.rating !in ReviewRules.RATING_MIN..ReviewRules.RATING_MAX) {
            val code = if (entry.rating == 0) ErrorCode.FieldRequired else ErrorCode.FieldInvalid
            add(FieldError("rating", code, "Choose from 1 to 5 stars"))
        }
        val title = entry.title.trim()
        when {
            title.isEmpty() -> add(FieldError("title", ErrorCode.FieldRequired, "Give the review a title"))
            title.length > ReviewRules.TITLE_MAX -> add(tooLong("title", ReviewRules.TITLE_MAX))
        }
        val body = entry.body.trim()
        when {
            body.isEmpty() -> add(FieldError("body", ErrorCode.FieldRequired, BODY_SHORT))
            body.length < ReviewRules.BODY_MIN -> add(FieldError("body", ErrorCode.FieldInvalid, BODY_SHORT))
            body.length > ReviewRules.BODY_MAX -> add(tooLong("body", ReviewRules.BODY_MAX))
        }
    }

/** Every field of [entry] at fault; empty when it can be sent. */
public fun questionProblems(entry: QuestionEntry): List<FieldError> =
    buildList {
        val text = entry.text.trim()
        when {
            text.isEmpty() -> add(FieldError("text", ErrorCode.FieldRequired, QUESTION_SHORT))
            text.length < ReviewRules.QUESTION_MIN -> add(FieldError("text", ErrorCode.FieldInvalid, QUESTION_SHORT))
            text.length > ReviewRules.QUESTION_MAX -> add(tooLong("text", ReviewRules.QUESTION_MAX))
        }
    }

/** «1,000», the way the dialogs write a limit. */
public fun groupedCount(value: Int): String =
    value
        .toString()
        .reversed()
        .chunked(3)
        .joinToString(",")
        .reversed()

private fun tooLong(
    field: String,
    max: Int,
): FieldError = FieldError(field, ErrorCode.FieldInvalid, "Keep it to ${groupedCount(max)} characters")

private const val BODY_SHORT = "Write at least ${ReviewRules.BODY_MIN} characters"
private const val QUESTION_SHORT = "Write at least ${ReviewRules.QUESTION_MIN} characters"
