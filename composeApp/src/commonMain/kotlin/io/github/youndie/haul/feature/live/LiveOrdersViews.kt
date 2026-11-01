package io.github.youndie.haul.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.LocalHaulCompact
import io.github.youndie.haul.ui.HaulButton
import io.github.youndie.haul.ui.HaulIcons
import io.github.youndie.haul.ui.Icon
import io.github.youndie.haul.ui.LiveLane
import io.github.youndie.haul.ui.LiveOrderRow
import io.github.youndie.haul.ui.LiveOrdersBody
import io.github.youndie.haul.ui.LiveStep
import io.github.youndie.haul.ui.LiveStepState
import io.github.youndie.haul.ui.LiveTrailLine
import io.github.youndie.haul.ui.Text
import io.github.youndie.haul.ui.accented
import io.github.youndie.haul.ui.following
import io.github.youndie.haul.ui.gutter
import io.github.youndie.haul.ui.offersLink
import io.github.youndie.haul.ui.pressable

// «Live orders» (`/live`, B-164): every order's saga as it runs. No artboard draws it; it borrows the About page's
// head (B-160) and the order page's paper cards. A row's steps are pills — a dot and the step — coloured by how they
// stand: Cobalt's dot for done, the error colour for the step that failed and for the ones its compensation undid, the
// outline for a step that had nothing to do or has not happened. A row opens in place to show its trail: which rows are
// open is the only thing the client keeps, by the order's number, so a row stays open as the live channel sends the
// body again.

/** The tag on the page's body. */
public const val LIVE_ORDERS_TAG: String = "live-orders"

/** The tag on the row of order [id]. */
public fun liveOrderTag(id: String): String = "live-order-$id"

/** The tag on the control that opens and closes the row of order [id]. */
public fun liveOrderToggleTag(id: String): String = "live-order-toggle-$id"

/** The tag on the trail of order [id], drawn while its row is open. */
public fun liveOrderTrailTag(id: String): String = "live-order-trail-$id"

/**
 * The page's body (`LiveOrdersBody` on the wire): the head, then the rows, newest first, then the note. The rows
 * [open] at first are drawn open — a screenshot's; a visitor's page opens none until pressed.
 */
@Composable
public fun LiveOrdersBodyView(
    body: LiveOrdersBody,
    open: Set<String> = emptySet(),
) {
    val compact = LocalHaulCompact.current
    var opened by remember { mutableStateOf(open) }
    Column(
        Modifier
            .testTag(LIVE_ORDERS_TAG)
            .fillMaxWidth()
            .padding(start = gutter(), end = gutter(), top = if (compact) 40.dp else 72.dp, bottom = 0.dp),
    ) {
        Text(body.eyebrow.uppercase(), HaulType.label(11f, 600, 0.08f).copy(color = HaulColors.outline))
        Text(
            accented(body.title, body.accent),
            Modifier.padding(top = if (compact) 14.dp else 18.dp).widthIn(max = 1000.dp),
            style =
                HaulType.display(
                    if (compact) 40f else 64f,
                    800,
                    letterSpacing = if (compact) -0.01f else -0.03f,
                    lineHeight = 0.98f,
                ),
        )
        Text(
            body.lead,
            HaulType.text(if (compact) 16f else 18f, lineHeight = 1.5f).copy(color = HaulColors.onSurfaceVariant),
            Modifier.padding(top = if (compact) 18.dp else 24.dp).widthIn(max = 820.dp),
        )
        if (body.links.isNotEmpty()) {
            FlowRow(
                Modifier.padding(top = if (compact) 24.dp else 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                body.links.forEach { entry ->
                    HaulButton(
                        entry.label,
                        Modifier.offersLink(entry.action),
                        height = if (compact) 48.dp else 52.dp,
                        radius = 16.dp,
                        border = HaulColors.onSurface,
                        textSize = if (compact) 15f else 16f,
                        onClick = following(entry.action),
                    )
                }
            }
        }
        Column(
            Modifier.padding(top = if (compact) 32.dp else 48.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
        ) {
            body.empty?.let { Empty(it) }
            body.orders.forEach { order ->
                val shown = order.id in opened
                Order(order, shown, if (shown) body.close else body.open) {
                    opened = if (shown) opened - order.id else opened + order.id
                }
            }
        }
        body.note?.let {
            Text(
                it,
                HaulType.text(if (compact) 13f else 14f, lineHeight = 1.5f).copy(color = HaulColors.outline),
                Modifier.padding(top = if (compact) 20.dp else 24.dp).widthIn(max = 820.dp),
            )
        }
    }
}

@Composable
private fun Empty(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(20.dp))
            .padding(28.dp),
    ) { Text(text, HaulType.text(16f, lineHeight = 1.5f).copy(color = HaulColors.onSurfaceVariant)) }
}

/** One order's card: its head, its lanes, and its trail while [open]; [toggle] opens and closes it. */
@Composable
private fun Order(
    order: LiveOrderRow,
    open: Boolean,
    toggleLabel: String,
    toggle: () -> Unit,
) {
    val compact = LocalHaulCompact.current
    Column(
        Modifier
            .testTag(liveOrderTag(order.id))
            .fillMaxWidth()
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(20.dp))
            .padding(horizontal = if (compact) 16.dp else 28.dp, vertical = if (compact) 18.dp else 22.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 16.dp),
    ) {
        Head(order, open, toggleLabel, toggle)
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 10.dp)) {
            order.lanes.forEach { Lane(it) }
        }
        if (open) Trail(order)
    }
}

@Composable
private fun Head(
    order: LiveOrderRow,
    open: Boolean,
    toggleLabel: String,
    toggle: () -> Unit,
) {
    val compact = LocalHaulCompact.current
    val number = HaulType.text(if (compact) 16f else 17f, 700)
    val meta = HaulType.text(if (compact) 13f else 14f, lineHeight = 1.4f).copy(color = HaulColors.outline)
    val sellers = HaulType.text(if (compact) 14f else 15f, lineHeight = 1.4f).copy(color = HaulColors.onSurfaceVariant)
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(order.number, number, Modifier.weight(1f))
                Status(order)
            }
            Text("${order.placed} · ${order.summary}", meta)
            Text(order.sellers, sellers)
            Toggle(order.id, open, toggleLabel, toggle)
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(order.number, number, Modifier.width(132.dp))
            Text(order.placed, meta, Modifier.width(112.dp))
            Text(order.sellers, sellers, Modifier.weight(1f))
            Text(order.summary, meta)
            Status(order)
            Toggle(order.id, open, toggleLabel, toggle)
        }
    }
}

@Composable
private fun Status(order: LiveOrderRow) {
    val compact = LocalHaulCompact.current
    Text(
        order.status,
        HaulType
            .text(
                if (compact) 12f else 13f,
                600,
            ).copy(color = if (order.undone) HaulColors.error else HaulColors.onSurface),
        Modifier
            .background(
                if (order.undone) HaulColors.errorContainer else HaulColors.surfaceContainerHigh,
                RoundedCornerShape(999.dp),
            ).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** «Show the trail» / «Hide the trail»: a press opens or closes the row; heard as a button and whether it is open. */
@Composable
private fun Toggle(
    id: String,
    open: Boolean,
    label: String,
    toggle: () -> Unit,
) {
    val compact = LocalHaulCompact.current
    Row(
        Modifier
            .testTag(liveOrderToggleTag(id))
            .semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = if (open) "open" else "closed"
            }.pressable(toggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            HaulType.text(if (compact) 13f else 14f, 600).copy(textDecoration = TextDecoration.Underline),
        )
        Icon(HaulIcons.chevronDown, 14.dp, HaulColors.onSurface, Modifier.rotate(if (open) 180f else 0f))
    }
}

/** A lane: its title beside its steps at 1440, above them on a phone. */
@Composable
private fun Lane(lane: LiveLane) {
    val compact = LocalHaulCompact.current
    val title = HaulType.label(11f, 600, 0.06f).copy(color = HaulColors.outline)
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(lane.title.uppercase(), title)
            Steps(lane.steps)
        }
    } else {
        Row(verticalAlignment = Alignment.Top) {
            Text(lane.title.uppercase(), title, Modifier.width(180.dp).padding(top = 8.dp, end = 16.dp))
            Box(Modifier.weight(1f)) { Steps(lane.steps) }
        }
    }
}

@Composable
private fun Steps(steps: List<LiveStep>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        steps.forEach { Step(it) }
    }
}

/**
 * A step's pill: its dot and its label, then what it says when that is more than «done» — the refusal, «undone»,
 * «no window» — or, done, its time. Heard as the label and the word, with the time.
 */
@Composable
private fun Step(step: LiveStep) {
    val compact = LocalHaulCompact.current
    val look = Look.of(step.state)
    val size = if (compact) 12f else 13f
    val heard = listOfNotNull(step.label, step.word, step.at).joinToString(", ")
    Row(
        Modifier
            .clearAndSetSemantics { contentDescription = heard }
            .height(if (compact) 28.dp else 30.dp)
            .then(if (look.fill != null) Modifier.background(look.fill, CircleShape) else Modifier)
            .then(if (look.border != null) Modifier.border(1.dp, look.border, CircleShape) else Modifier)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .then(
                    if (look.dot == null) {
                        Modifier.border(1.dp, HaulColors.outlineControl, CircleShape)
                    } else {
                        Modifier.background(look.dot, CircleShape)
                    },
                ),
        )
        Text(
            step.label,
            HaulType.text(size, 600).copy(
                color = look.text,
                textDecoration = if (step.state == LiveStepState.Undone) TextDecoration.LineThrough else null,
            ),
        )
        // Done, its time, or what it says beyond «done» («charged $64»); the rest, what they say; a step not reached
        // yet, nothing — it is drawn grey.
        val aside =
            when (step.state) {
                LiveStepState.Done -> {
                    listOfNotNull(
                        step.word.takeIf { it != DONE },
                        step.at,
                    ).joinToString(" · ").ifEmpty { null }
                }

                LiveStepState.Pending -> {
                    null
                }

                else -> {
                    step.word
                }
            }
        aside?.let { Text(it, HaulType.text(size).copy(color = look.aside)) }
    }
}

/** How a step's pill is drawn for its state. */
private class Look(
    val fill: Color?,
    val border: Color?,
    val dot: Color?,
    val text: Color,
    val aside: Color,
) {
    companion object {
        @Composable
        @ReadOnlyComposable
        fun of(state: LiveStepState): Look =
            when (state) {
                LiveStepState.Done -> {
                    Look(HaulColors.background, null, HaulColors.primary, HaulColors.onSurface, HaulColors.outline)
                }

                LiveStepState.Failed -> {
                    Look(HaulColors.errorContainer, null, HaulColors.error, HaulColors.error, HaulColors.error)
                }

                LiveStepState.Undone -> {
                    Look(null, HaulColors.error, HaulColors.error, HaulColors.error, HaulColors.error)
                }

                LiveStepState.Skipped, LiveStepState.Pending -> {
                    Look(null, HaulColors.outlineVariant, null, HaulColors.outlineMuted, HaulColors.outlineMuted)
                }
            }
    }
}

/** The trail: every step with its time to the second, the saga's members by their keys. */
@Composable
private fun Trail(order: LiveOrderRow) {
    val compact = LocalHaulCompact.current
    val time = HaulType.label(12f, 500, 0.02f).copy(color = HaulColors.outline)
    val member = HaulType.label(12f, 500, 0.02f).copy(color = HaulColors.outlineMuted)
    Column(Modifier.testTag(liveOrderTrailTag(order.id)).fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(HaulColors.outlineVariant))
        Column(
            Modifier.padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 8.dp),
        ) {
            order.trail.forEach { line ->
                val words =
                    HaulType.text(if (compact) 14f else 15f, lineHeight = 1.4f).copy(
                        color =
                            when (line.state) {
                                LiveStepState.Failed, LiveStepState.Undone -> HaulColors.error
                                LiveStepState.Skipped, LiveStepState.Pending -> HaulColors.outlineMuted
                                LiveStepState.Done -> HaulColors.onSurface
                            },
                    )
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(listOfNotNull(line.at, line.member).joinToString("  "), time)
                        Text(line.text, words)
                    }
                } else {
                    TrailLine(line, time, member, words)
                }
            }
        }
    }
}

@Composable
private fun TrailLine(
    line: LiveTrailLine,
    time: TextStyle,
    member: TextStyle,
    words: TextStyle,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(line.at, time, Modifier.width(156.dp))
        Text(line.text, words, Modifier.weight(1f).padding(end = 16.dp))
        line.member?.let { Text(it, member) }
    }
}

private const val DONE = "done"
