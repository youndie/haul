package io.github.youndie.haul.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.LocalHaulCompact

// What the stand says about itself (B-160): the demo strip above every page and the About page's body.

/** The tag on the demo strip, for the tests that look for it. */
public const val DEMO_NOTE_TAG: String = "demo-note"

/** The tag on the demo strip's close control. */
public const val DEMO_NOTE_CLOSE_TAG: String = "demo-note-close"

/**
 * The demo strip (`DemoNote` on the wire): one quiet line above the page — Paper's grey under the header's ink strip,
 * the store's text type at its smallest — saying what the store is, then its links, underlined, then the close
 * control, [onClose]; without one (a tree that carried the note) nothing closes it. At 1440 it is one line; on a
 * phone the words wrap and the links take the line under them. The links follow their actions: «About this demo»
 * opens the About page, «Source on GitHub» the repository in a tab of its own.
 *
 * [onHood] is the «Under the hood» toggle's press (B-163), drawn after the links — on a phone on a line of its own —
 * as a pill named by the note's `hood`, filled while [hoodOpen]; without a press or a name there is no toggle, so a
 * strip drawn on its own (a golden) is as before.
 */
@Composable
public fun DemoNoteView(
    note: DemoNote,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onHood: (() -> Unit)? = null,
    hoodOpen: Boolean = false,
) {
    val hood = note.hood?.takeIf { onHood != null }
    val compact = LocalHaulCompact.current
    val size = if (compact) 12f else 13f
    val words = HaulType.text(size, lineHeight = 1.4f).copy(color = HaulColors.onSurfaceVariant)
    val link = HaulType.text(size, 600, lineHeight = 1.4f).copy(textDecoration = TextDecoration.Underline)
    Row(
        modifier
            .testTag(DEMO_NOTE_TAG)
            .fillMaxWidth()
            .background(HaulColors.surfaceContainerHigh)
            .padding(
                start = gutter(),
                end =
                    if (onClose == null) {
                        gutter()
                    } else if (compact) {
                        6.dp
                    } else {
                        38.dp
                    },
            ).padding(vertical = if (compact) 8.dp else 7.dp),
        verticalAlignment = if (compact) Alignment.Top else Alignment.CenterVertically,
    ) {
        if (compact) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(note.text, words)
                NoteLinks(note.links, link)
                if (hood != null && onHood != null) {
                    HoodToggle(hood, hoodOpen, onHood, link, Modifier.padding(top = 6.dp, bottom = 2.dp))
                }
            }
        } else {
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(note.text, words)
                NoteLinks(note.links, link)
                if (hood != null && onHood != null) HoodToggle(hood, hoodOpen, onHood, link)
            }
        }
        if (onClose != null) {
            Box(
                Modifier
                    .testTag(DEMO_NOTE_CLOSE_TAG)
                    .size(if (compact) 28.dp else 24.dp)
                    .semantics {
                        contentDescription = note.close
                        role = Role.Button
                    }.pressable(onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(HaulIcons.close, 12.dp, HaulColors.onSurfaceVariant) }
        }
    }
}

/** The tag on the strip's «Under the hood» toggle. */
public const val HOOD_TOGGLE_TAG: String = "under-the-hood-toggle"

/**
 * «Under the hood» (B-163): a pill drawn around the words without growing the line — its edge reaches into the strip's
 * padding — outlined while the panel is closed, filled with the ink while it is open; a switch to assistive technology.
 */
@Composable
private fun HoodToggle(
    label: String,
    open: Boolean,
    onPress: () -> Unit,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val state = if (open) ToggleableState.On else ToggleableState.Off
    Box(
        modifier
            .testTag(HOOD_TOGGLE_TAG)
            .toggles(label, state, Role.Switch, onPress)
            .drawBehind {
                val reach = 3.dp.toPx()
                val corner = CornerRadius(size.height / 2 + reach)
                val topLeft = Offset(0f, -reach)
                val pill = Size(size.width, size.height + 2 * reach)
                if (open) {
                    drawRoundRect(HaulColors.inverseSurface, topLeft, pill, corner)
                } else {
                    drawRoundRect(HaulColors.outline, topLeft, pill, corner, style = Stroke(1.dp.toPx()))
                }
            }.padding(horizontal = 10.dp),
    ) {
        Text(
            label,
            style.copy(textDecoration = null, color = if (open) HaulColors.inverseOnSurface else HaulColors.onSurface),
        )
    }
}

/** The strip's links, a dot apart. */
@Composable
private fun NoteLinks(
    links: List<Link>,
    style: TextStyle,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        links.forEachIndexed { index, entry ->
            if (index > 0) Text("·", style.copy(textDecoration = null, color = HaulColors.outline))
            Text(entry.label, style, Modifier.follows(entry.action))
        }
    }
}

/** The tag on the About page's body. */
public const val ABOUT_TAG: String = "about"

/**
 * The About page's body (`AboutBody` on the wire, `/about`): the eyebrow, the title in the display face with its
 * accent in italic, the lead; then each section under a rule — its title beside its points at 1440, above them on a
 * phone, each point its name and what it says — and the links at the end as the store's buttons, the first filled.
 */
@Composable
public fun AboutBodyView(body: AboutBody) {
    val compact = LocalHaulCompact.current
    Column(
        Modifier
            .testTag(ABOUT_TAG)
            .fillMaxWidth()
            .padding(start = gutter(), end = gutter(), top = if (compact) 48.dp else 96.dp, bottom = 0.dp),
    ) {
        Text(body.eyebrow.uppercase(), HaulType.label(11f, 600, 0.08f).copy(color = HaulColors.outline))
        Text(
            accented(body.title, body.accent),
            Modifier.padding(top = if (compact) 16.dp else 20.dp).widthIn(max = 1000.dp),
            style =
                HaulType.display(
                    if (compact) 40f else 72f,
                    800,
                    letterSpacing = if (compact) -0.01f else -0.03f,
                    lineHeight = 0.98f,
                ),
        )
        Text(
            body.lead,
            HaulType.text(if (compact) 17f else 20f, lineHeight = 1.5f).copy(color = HaulColors.onSurfaceVariant),
            Modifier.padding(top = if (compact) 20.dp else 28.dp).widthIn(max = 760.dp),
        )
        Column(
            Modifier.padding(top = if (compact) 40.dp else 72.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 36.dp else 56.dp),
        ) {
            body.sections.forEach { AboutSectionView(it, compact) }
        }
        if (body.links.isNotEmpty()) {
            FlowRow(
                Modifier.padding(top = if (compact) 40.dp else 64.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                body.links.forEachIndexed { index, entry ->
                    HaulButton(
                        entry.label,
                        Modifier.offersLink(entry.action),
                        height = if (compact) 52.dp else 56.dp,
                        radius = 16.dp,
                        fill = if (index == 0) HaulColors.primary else null,
                        content = if (index == 0) HaulColors.onPrimary else HaulColors.onSurface,
                        border = if (index == 0) null else HaulColors.onSurface,
                        textSize = if (compact) 16f else 17f,
                        icon = if (index == 0) HaulIcons.arrowRight else null,
                        onClick = following(entry.action),
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutSectionView(
    section: AboutSection,
    compact: Boolean,
) {
    val title = HaulType.display(if (compact) 26f else 32f, 800, letterSpacing = -0.01f, lineHeight = 1.05f)
    val name = HaulType.text(if (compact) 16f else 17f, 700, lineHeight = 1.4f)
    val text = HaulType.text(if (compact) 15f else 16f, lineHeight = 1.5f).copy(color = HaulColors.onSurfaceVariant)
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(HaulColors.outlineVariant))
        if (compact) {
            Text(section.title, title, Modifier.padding(top = 20.dp))
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                section.points.forEach { point ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(point.name, name)
                        Text(point.text, text)
                    }
                }
            }
        } else {
            Row(Modifier.padding(top = 28.dp)) {
                Text(section.title, title, Modifier.weight(0.34f).padding(end = 32.dp))
                Column(Modifier.weight(0.66f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    section.points.forEach { point ->
                        Row {
                            Text(point.name, name, Modifier.weight(0.36f).padding(end = 24.dp))
                            Text(point.text, text, Modifier.weight(0.64f))
                        }
                    }
                }
            }
        }
    }
}
