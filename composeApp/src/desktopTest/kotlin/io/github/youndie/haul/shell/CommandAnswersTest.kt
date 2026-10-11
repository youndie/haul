package io.github.youndie.haul.shell

import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.standard.CloseAction
import io.github.youndie.kompot.standard.MessageLevel
import io.github.youndie.kompot.standard.RefreshAction
import io.github.youndie.kompot.standard.SequenceAction
import io.github.youndie.kompot.standard.ShowMessageAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * B-92's rule, the two answers every command family builds on: a refusal is the page drawn again and the server's
 * words in an error message — the words alone when the tree draws them at the control — and no answer is «Couldn't
 * reach the store», with «Retry» only when the press may be sent again.
 */
class CommandAnswersTest {
    @Test
    fun `a refusal draws the page again and then says why in the server's words`() {
        assertEquals(
            SequenceAction(listOf(RefreshAction, ShowMessageAction("Only 3 left in stock", MessageLevel.ERROR))),
            refusal("Only 3 left in stock"),
        )
    }

    @Test
    fun `a refusal the tree draws is the page drawn again and nothing more`() {
        assertEquals(RefreshAction, refusal("This code has expired", drawn = true))
    }

    @Test
    fun `a refusal that named nothing says it did not go through`() {
        assertEquals(told(NOT_SENT), refusal(null))
    }

    @Test
    fun `a refusal follows what it is given before the words`() {
        val closed = SequenceAction(listOf(CloseAction, RefreshAction))
        assertEquals(
            told("You are a Haul Plus member already", closed),
            refusal("You are a Haul Plus member already", then = closed),
        )
        assertEquals(ShowMessageAction("No such order", MessageLevel.ERROR), refusal("No such order", then = null))
    }

    @Test
    fun `no answer says so with Retry which is the press given`() {
        var pressed = 0
        val again = assertNoAnswer(noAnswer { pressed += 1 }, retry = true)
        again?.press?.invoke()
        assertEquals(1, pressed)
    }

    @Test
    fun `no answer without a press to send again has no button`() {
        assertNoAnswer(noAnswer(null), retry = false)
    }
}

/** What a refused command answers (B-92): [then], the page drawn again, followed by the server's [reason]. */
internal fun told(
    reason: String,
    then: KompotAction = RefreshAction,
): KompotAction = SequenceAction(listOf(then, ShowMessageAction(reason, MessageLevel.ERROR)))

/**
 * Checks that [action] is B-92's «Couldn't reach the store» — an error message, with «Retry» when [retry] — and
 * returns its «Retry», the press it sends again, or `null` when it has none.
 */
internal fun assertNoAnswer(
    action: KompotAction?,
    retry: Boolean,
): PressAgain? {
    val message = assertIs<ShowMessageAction>(action, "not a message: $action")
    assertEquals(NO_ANSWER, message.text)
    assertEquals(MessageLevel.ERROR, message.level)
    if (!retry) {
        assertNull(message.actionLabel, "a press that may not be sent twice offers «Retry»")
        assertNull(message.action)
        return null
    }
    assertEquals(RETRY, message.actionLabel)
    return assertIs<PressAgain>(message.action)
}
