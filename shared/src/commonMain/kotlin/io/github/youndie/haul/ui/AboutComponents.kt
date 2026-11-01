package io.github.youndie.haul.ui

import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.registry.KompotComponentMarker
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// What the stand says about itself (B-160): that it is a demo, what it is built with, and where its code is. The
// copy is the server's, as every page's is; the client draws it.

/**
 * The demo strip: one quiet line above every page of the storefront, until the visitor closes it — [text], what the
 * store is, and [links] after it: «About this demo» (`navigate` to `/about`) and «Source on GitHub» (`open_url` to
 * the repository, which the client opens in a tab of its own). [close] is the close control's name for assistive
 * technology; closing is the client's, remembered for the session, and sends nothing. [hood] is the name of the
 * «Under the hood» toggle (B-163), which opens the client's panel over the page — the screen's tree, the request that
 * brought it and the updates that replace its parts; the panel is the client's and sends nothing, and a strip without
 * the name draws no toggle.
 *
 * Not part of any page's tree: the storefront asks for it on its own (`GET /ui/demo`) once the first page has drawn,
 * and draws it above whatever page is shown. In the header it would have moved every page's golden and every
 * client body (B-160).
 */
@Serializable
@SerialName("haul_demo_note")
@KompotComponentMarker
public data class DemoNote(
    override val id: String,
    val text: String,
    val links: List<Link>,
    val close: String,
    val hood: String? = null,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/**
 * The About page's body (`/about`, B-160): an [eyebrow], a [title] with its [accent] — the words of it set in the
 * accent's italic — and a [lead] paragraph, then [sections] — what the stack is, what is real, what is not — and the
 * [links] at its end: the repository and its README, each opened in a tab of its own.
 */
@Serializable
@SerialName("haul_about")
@KompotComponentMarker
public data class AboutBody(
    override val id: String,
    val eyebrow: String,
    val title: String,
    val accent: String? = null,
    val lead: String,
    val sections: List<AboutSection>,
    val links: List<Link> = emptyList(),
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/** A section of the About page: its [title] and its [points], each a name and what it says. */
@Serializable
public data class AboutSection(
    val title: String,
    val points: List<AboutPoint>,
)

/** One point of an [AboutSection]: [name] — «Ktor on the JVM» — and [text], what it is and does here. */
@Serializable
public data class AboutPoint(
    val name: String,
    val text: String,
)
