package io.github.youndie.haul.ui

import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.registry.KompotComponentMarker
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Haul Plus on the wire (feature-membership, B-23). The trial is offered in three places — the home page's
// Plus block, the account's Haul Plus tile, and nowhere for a member — and each offers it the same way: a
// kompot `present` of [PlusTrialDialog] over the page (`Home_PlusTrialDialog`).

/** One benefit of Haul Plus as the trial dialog lists it: «Free delivery on every order», «No minimum basket». */
@Serializable
public data class PlusBenefit(
    val title: String,
    val detail: String,
)

/**
 * The trial's dialog: the [eyebrow] («Haul Plus») and the [title] with its [accent] in italics («30 days
 * *free*») on black, then the [benefits], a hairline, the [terms] («30 days free, then $4.99/month») and
 * the two buttons. [startLabel] sends a `POST` with no body to [url], answered with kompot's `sequence`
 * of `close` and `refresh`; [dismissLabel] and «×» are [close].
 */
@Serializable
@SerialName("haul_plus_trial_dialog")
@KompotComponentMarker
public data class PlusTrialDialog(
    override val id: String,
    val eyebrow: String,
    val title: String,
    val accent: String? = null,
    val benefits: List<PlusBenefit>,
    val terms: String,
    val startLabel: String,
    val dismissLabel: String,
    val url: String,
    val close: @Polymorphic KompotAction? = null,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent
