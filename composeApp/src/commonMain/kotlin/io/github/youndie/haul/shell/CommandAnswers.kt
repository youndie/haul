package io.github.youndie.haul.shell

import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.standard.MessageLevel
import io.github.youndie.kompot.standard.RefreshAction
import io.github.youndie.kompot.standard.SequenceAction
import io.github.youndie.kompot.standard.ShowMessageAction

// One rule for every command a press sends (B-92, the owner's decision) — the cart's, the Saved list's heart, the
// checkout's, an order's «Reorder», the dialogs' and «Helpful»: an answer is followed; a refusal draws the page as
// the server now has it and says why in the server's words; no answer at all says so — «Couldn't reach the store» —
// with «Retry» when sending the press again cannot do it twice. Both are B-75's message over the page the press
// was on (`Messages.kt`), except in a dialog with a place of its own for what went wrong (the review, question
// and return dialogs), and except a refusal the next tree already draws at the control. A `load` that does not
// arrive has said so since B-62, under its own notice.

/** What a press says when its command got no answer at all. */
public const val NO_ANSWER: String = "Couldn’t reach the store"

/** The button on [NO_ANSWER] when the press may be sent again: the word B-62's notice has. */
public const val RETRY: String = "Retry"

/**
 * «Retry» on a message: the same press, sent again. The client's own action — no tree carries one, and it never goes
 * on the wire — which the screen's handler runs when the message's button is pressed.
 */
public class PressAgain(
    public val press: () -> Unit,
) : KompotAction

/**
 * A command that got no answer: the page stays as it was and says so, with «Retry» — [again], the same press — when
 * one is given. A command is given one when sending it twice does what sending it once does: a `PUT`, a `DELETE`,
 * and the placement, whose key makes a retry the same order (B-16); a `POST` that adds — «Reorder», a review — is not.
 */
public fun noAnswer(again: (() -> Unit)?): ShowMessageAction =
    ShowMessageAction(NO_ANSWER, MessageLevel.ERROR, again?.let { RETRY }, again?.let(::PressAgain))

/**
 * A command the server refused: [then] — by default `refresh`, the page drawn as the server now has it — and the
 * server's [reason] in a message, unless the tree [then] brings draws it at the control already ([drawn]): a refused
 * promo code under its field (Cart_PromoError), an address form's errors under its fields (Checkout_Validation), a
 * window that filled up at placement (Checkout_PlaceError). A refusal whose body named nothing (a proxy's page) says
 * [NOT_SENT].
 */
public fun refusal(
    reason: String?,
    drawn: Boolean = false,
    then: KompotAction? = RefreshAction,
): KompotAction {
    val told = if (drawn) null else ShowMessageAction(reason ?: NOT_SENT, MessageLevel.ERROR)
    val actions = listOfNotNull(then, told)
    return actions.singleOrNull() ?: SequenceAction(actions)
}
