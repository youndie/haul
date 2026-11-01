package io.github.youndie.haul.feature.membership.screen

import io.github.youndie.haul.feature.account.domain.Standing
import io.github.youndie.haul.feature.catalog.domain.money
import io.github.youndie.haul.feature.membership.MembershipPaths
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.ui.PlusBenefit
import io.github.youndie.haul.ui.PlusBlock
import io.github.youndie.haul.ui.PlusTrialDialog
import io.github.youndie.haul.ui.PromoBanner
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.standard.CloseAction
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.PresentAction
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Haul Plus as the screens offer it (feature-membership, screen-home, screen-account): «Try 30 days free»
 * presents the trial's dialog (`Home_PlusTrialDialog`) to a customer and asks a guest to sign in first;
 * a member is never offered it (research D6) and sees what the membership saved instead.
 */
internal object PlusOffer {
    /** What kompot's `present` is asked to show the trial as: a dialog over the page. */
    const val DIALOG = "dialog"

    /**
     * The trial's dialog, the canvas's copy; «Start trial» is `POST` [MembershipPaths.TRIAL]. Each benefit is a rule
     * the store applies (B-172, `PromisesAgreeTest`): the canvas's «Next-day courier · Whatever the order total» was
     * not — every shopper's courier day is the same (`DeliveryCalendar`), member or not, whatever the total.
     */
    val dialog: PlusTrialDialog =
        PlusTrialDialog(
            id = "plus-trial",
            eyebrow = "Haul Plus",
            title = "30 days free",
            accent = "free",
            benefits =
                listOf(
                    PlusBenefit("Free delivery on every order", "No minimum basket"),
                    PlusBenefit("Double points", "2 points for every dollar"),
                    PlusBenefit("Early access to sales", "Campaign prices 24 hours early"),
                ),
            terms = "30 days free, then $4.99/month",
            startLabel = "Start trial",
            dismissLabel = "Not now",
            url = MembershipPaths.TRIAL,
            close = CloseAction,
        )

    /** «Try 30 days free» for [viewer]: the dialog for a customer, sign-in for a guest. */
    fun trial(viewer: Viewer): KompotAction =
        if (viewer.customerId == null) NavigateAction(Frame.SIGN_IN) else PresentAction(dialog, DIALOG)

    /**
     * The home page's Plus block: the offer to a guest and a non-member ([standing] `null` or without a
     * membership), what the membership saved this year and when it renews to a member («You saved $186 on
     * delivery this year», «Renews Nov 2»). A member with nothing saved yet reads the offer's headline.
     */
    fun block(
        viewer: Viewer,
        standing: Standing?,
    ): PlusBlock {
        val membership = standing?.membership ?: return offer(viewer)
        val saved = membership.savedCents.takeIf { it > 0 }?.let(::money)
        return PlusBlock(
            id = "plus",
            member = true,
            title = saved?.let { "You saved $it on delivery this year" } ?: HEADLINE,
            accent = saved ?: HEADLINE_ACCENT,
            savings = saved,
            renewal = membership.renews?.let { "Renews " + MONTH_DAY.format(it) },
            price = "$4.99 / month",
            benefits =
                listOf(
                    "Free delivery on every order",
                    "Double points on every purchase",
                    "Early access to sales",
                ),
        )
    }

    /**
     * Haul Plus's banner beside the sale's hero (B-172), in the place of the free-delivery weekend's «Free delivery on
     * everything», a rule the cart never applied (delivery is free from $35, research D7): free delivery on every
     * order is what Plus does, so the banner says whose promise it is. To a guest and a non-member it is the offer —
     * «Try 30 days free», the trial as the pill and the block present it ([trial], B-59's Plus clause); to a member,
     * who is never offered the trial (research D6), it is the membership's mark with nothing to press, as the pill is
     * (B-108).
     */
    fun banner(viewer: Viewer): PromoBanner {
        val member = viewer.customer?.plus == true
        return PromoBanner(
            id = "banner-plus",
            eyebrow = "With Haul Plus",
            title = "Free delivery on everything",
            subtitle = "Included in your membership".takeIf { member },
            actionLabel = "Try 30 days free".takeUnless { member },
            tone = BANNER_TONE,
            accent = "everything",
            action = trial(viewer).takeUnless { member },
        )
    }

    private fun offer(viewer: Viewer): PlusBlock =
        PlusBlock(
            id = "plus",
            member = false,
            title = HEADLINE,
            benefits =
                listOf(
                    "No minimum basket",
                    "Early access to sales",
                    "Double points on every purchase",
                ),
            offer = "Try 30 days free",
            price = "then $4.99 / month",
            action = trial(viewer),
            accent = HEADLINE_ACCENT,
        )

    private const val HEADLINE = "Free delivery. Every order."

    /** Acid, the free-delivery weekend's banner's tone, which this banner took the place of. */
    private const val BANNER_TONE = "#DFFF3A"
    private const val HEADLINE_ACCENT = "Every"
    private val MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US)
}
