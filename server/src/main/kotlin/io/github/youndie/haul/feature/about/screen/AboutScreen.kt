package io.github.youndie.haul.feature.about.screen

import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.ui.AboutBody
import io.github.youndie.haul.ui.AboutPoint
import io.github.youndie.haul.ui.AboutSection
import io.github.youndie.haul.ui.DemoNote
import io.github.youndie.haul.ui.Link
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.OpenUrlAction

/**
 * What the stand says about itself (B-160). Four reviews of the stand found nothing on it said it was a demo or what
 * it was built with: a shopper read it as a real store that could not be trusted, a developer never learned it was
 * Kotlin end to end, or where the code was.
 *
 * - [note] is the demo strip the storefront draws above every page until it is closed (`GET /ui/demo`). It is a tree
 *   of its own, not a part of the header: in the header it would have moved every page's golden and every client body.
 * - [page] is «About this demo» (`GET /ui/about`, the page at `/about`): the frame — header, the body, the footer —
 *   around what the stack is, what is real and what is not. The footer is left as it is: a link there would move every
 *   footer's golden, and the strip already leads here on every page.
 *
 * The same for everyone: who is looking changes only the header.
 */
internal object AboutScreen {
    /** The address of the About page, where «About this demo» goes. */
    const val ADDRESS = "/about"

    /** The repository, where «Source on GitHub» goes. */
    const val REPOSITORY = "https://github.com/youndie/haul"

    /** The repository's README, as GitHub shows it under the files. */
    const val README = "$REPOSITORY#readme"

    fun note(): DemoNote =
        DemoNote(
            id = "demo-note",
            text = "A demo store built with Kotlin: Ktor, kompot, Compose Multiplatform in the browser.",
            links =
                listOf(
                    Link("About this demo", NavigateAction(ADDRESS)),
                    Link("Source on GitHub", OpenUrlAction(REPOSITORY)),
                ),
            close = "Close the demo note",
        )

    fun page(
        viewer: Viewer,
        navigation: List<Link>,
    ): KompotComponent = Frame.page("about-page", viewer, navigation, listOf(body()), footer = true)

    fun body(): AboutBody =
        AboutBody(
            id = "about",
            eyebrow = "About this demo",
            title = "A demo store, built with Kotlin end to end",
            accent = "Kotlin end to end",
            lead =
                "Haul is an open showcase: a marketplace whose every page, command and order runs on one Kotlin " +
                    "stack, from the database to the pixels. Browse it, fill a cart, sign in, place an order and " +
                    "return it — nothing done here costs anything or reaches anybody.",
            sections = listOf(STACK, REAL, NOT_REAL),
            links =
                listOf(
                    Link("Source on GitHub", OpenUrlAction(REPOSITORY)),
                    Link("Read the README", OpenUrlAction(README)),
                ),
        )

    private val STACK =
        AboutSection(
            "What it is built with",
            listOf(
                AboutPoint(
                    "Ktor on the JVM",
                    "The server: every page, every command and the order’s saga, over PostgreSQL. It starts from an " +
                        "AOT cache zavarnik trains when the image is built.",
                ),
                AboutPoint(
                    "kompot",
                    "Every page is a tree of components the server builds and sends, and the browser draws. A " +
                        "filter, a stepper or a tab answers with only the parts that changed.",
                ),
                AboutPoint(
                    "Compose Multiplatform in the browser",
                    "The storefront is Compose on Kotlin/Wasm; the same code draws the screenshot tests on the JVM.",
                ),
                AboutPoint(
                    "petich",
                    "An order is a saga: stock, a delivery slot, points and the card are reserved in turn, and given " +
                        "back in reverse when a step fails.",
                ),
                AboutPoint(
                    "shildik",
                    "Sign-in is an OpenID Connect provider of the project’s own, deployed beside the store — the " +
                        "sign-in page on another address is it.",
                ),
                AboutPoint(
                    "Synthetic shoppers",
                    "Scripted shoppers walk the store every ten minutes — browse, buy, receive, return — so an order " +
                        "is always on its way.",
                ),
                AboutPoint(
                    "tracy",
                    "Logs and traces of every request, each order’s saga one list in them; route metrics in metrik, " +
                        "crash reports in katcher.",
                ),
            ),
        )

    private val REAL =
        AboutSection(
            "What is real",
            listOf(
                AboutPoint(
                    "The saga and its compensations",
                    "Placing an order reserves and authorises step by step; a declined card gives back everything " +
                        "taken before it, and a return is refunded.",
                ),
                AboutPoint(
                    "The sellers, couriers and payments",
                    "Simulated inside the server, on a clock: shipments are packed, sent and delivered, a card is " +
                        "charged as its shipment leaves, and the four payments come due.",
                ),
                AboutPoint(
                    "The observability",
                    "What the stand logs, traces and reports is what happened on it, the synthetic shoppers’ walks " +
                        "included.",
                ),
            ),
        )

    private val NOT_REAL =
        AboutSection(
            "What is not",
            listOf(
                AboutPoint("Money", "No money moves: the cards are the simulator’s, and nobody is charged."),
                AboutPoint("Parcels", "No parcel ships: a delivery is the simulator moving it along."),
                AboutPoint(
                    "Products, reviews and sellers",
                    "Invented for the demo: the catalogue, its pictures, the reviews and the sellers are sample " +
                        "data. No brand sells here.",
                ),
            ),
        )
}
