package io.github.youndie.haul.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.LocalHaulCompact
import io.github.youndie.haul.theme.LocalThemeChoice
import io.github.youndie.haul.theme.THEME_TITLE
import io.github.youndie.haul.theme.ThemeChoice
import io.github.youndie.haul.theme.ThemeMode

/**
 * The footer (`HaulFooter` on the wire), with the gap the page leaves above it: up to four link columns at 1440,
 * two columns a row at 390, the wordmark cut off by the page's end. A word follows its entry in
 * `FooterColumn.linked` (B-72); one without is drawn as text, nothing to press. The footer keeps the canvas's
 * frame with fewer words in it: four column places at 1440 and the place of the canvas's «Get the app» block at
 * the end, left empty since there is no app (B-107), two rows of two on a phone, four lines to a column — so it
 * is as tall as the canvas draws it and the wordmark is cut where it was.
 */
@Composable
public fun HaulFooterView(footer: HaulFooter) {
    val compact = LocalHaulCompact.current
    val theme = LocalThemeChoice.current
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(if (compact) 64.dp else 80.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(HaulColors.band)
                .clipToBounds()
                .padding(
                    start = if (compact) 16.dp else 48.dp,
                    end = if (compact) 16.dp else 48.dp,
                    top = if (compact) 40.dp else 64.dp,
                ),
        ) {
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    val rows = footer.columns.chunked(2)
                    rows.forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            pair.forEach { LinkColumn(it, compact = true, Modifier.weight(1f)) }
                            repeat(2 - pair.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                    // A row of columns the canvas has and the wire does not: its height, empty.
                    repeat(COLUMNS / 2 - rows.size) { Spacer(Modifier.height(columnHeight(compact = true))) }
                    // The theme's switch, on a row of its own under the canvas's two (B-180).
                    if (theme != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            ThemeColumn(theme, compact = true, Modifier.weight(1f))
                            Box(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    footer.columns.forEach { LinkColumn(it, compact = false, Modifier.weight(1f)) }
                    repeat(COLUMNS - footer.columns.size) { Box(Modifier.weight(1f)) }
                    // The canvas's «Get the app» place: there is no app (B-107), and the storefront puts the theme's
                    // switch there (B-180); empty where nothing holds one — a golden.
                    Box(Modifier.weight(1.4f)) { if (theme != null) ThemeColumn(theme, compact = false) }
                }
            }
            // On a desktop as large as the page is wide enough for (B-82): 420 px of «Haul.» ran past the edge at
            // 1024. The phone's runs past it at 390, as Home_Content_Phone draws it, and is left so.
            val size = if (compact) 150f else 420f
            // A picture of the name, not words to copy, and out of the page's selection (B-155): selectable, it took
            // the presses of the column words above it — its text runs past its box — and «Haul Plus» opened nothing.
            val acid = HaulColors.secondaryContainer
            DisableSelection {
                FittedText(
                    buildAnnotatedString {
                        append("Haul")
                        withStyle(SpanStyle(color = HaulColors.primary)) { append(".") }
                    },
                    size = size,
                    min = if (compact) size else size / 2,
                    style = { fonts, at ->
                        HaulType
                            .display(fonts, at, 900, letterSpacing = -0.05f, lineHeight = 0.78f)
                            .copy(color = acid)
                    },
                    modifier =
                        Modifier
                            .padding(top = if (compact) 40.dp else 56.dp)
                            .offset(x = if (compact) (-6).dp else (-14).dp)
                            .negativeBottomMargin(if (compact) 24.dp else 70.dp),
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun LinkColumn(
    column: FooterColumn,
    compact: Boolean,
    modifier: Modifier,
) {
    val style =
        HaulType
            .text(
                if (compact) 14f else 15f,
                lineHeight = 2f,
            ).copy(color = HaulColors.onBand)
    Column(modifier.heightIn(min = columnHeight(compact))) {
        ColumnTitle(column.title, compact)
        column.links.forEach { word ->
            // Hovered, a link leans on (B-158).
            val hover = rememberHover()
            Text(
                word,
                style,
                Modifier
                    .onHover(hover) { translationX = LINK_LEAN.toPx() * it }
                    .hovers(hover)
                    .follows(column.linked.firstOrNull { it.label == word }?.action),
            )
        }
    }
}

/**
 * The theme's switch (B-180): a column of the footer's, «Theme» over «System», «Light» and «Dark», the chosen one in
 * Acid with a dot before it. The footer is where a shopper looks for what is the site's rather than a page's — and it
 * is on every page but checkout, at both widths, where the header has no room left at 390.
 */
@Composable
private fun ThemeColumn(
    theme: ThemeChoice,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val style =
        HaulType
            .text(
                if (compact) 14f else 15f,
                lineHeight = 2f,
            ).copy(color = HaulColors.onBand)
    Column(modifier.heightIn(min = columnHeight(compact)).testTag(THEME_SWITCH_TAG)) {
        ColumnTitle(THEME_TITLE, compact)
        ThemeMode.entries.forEach { mode ->
            val chosen = theme.mode == mode
            val hover = rememberHover()
            Row(
                Modifier
                    .testTag(themeTag(mode))
                    .onHover(hover) { translationX = LINK_LEAN.toPx() * it }
                    .hovers(hover)
                    .toggles(
                        mode.label,
                        if (chosen) ToggleableState.On else ToggleableState.Off,
                        Role.RadioButton,
                        onPress = { theme.choose(mode) },
                    ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (chosen) Box(Modifier.size(7.dp).background(HaulColors.secondaryContainer, CircleShape))
                Text(
                    mode.label,
                    if (chosen) style.copy(color = HaulColors.secondaryContainer, fontWeight = FontWeight(700)) else style,
                    softWrap = false,
                )
            }
        }
    }
}

/** The tag of the footer's theme switch, for the tests that find it. */
public const val THEME_SWITCH_TAG: String = "theme-switch"

/** The tag of one of the switch's choices. */
public fun themeTag(mode: ThemeMode): String = "theme:${mode.key}"

/** How far a hovered link leans on. */
private val LINK_LEAN = 6.dp

/** How many link columns the canvas lays out: in a row at 1440, two rows of two on a phone. */
private const val COLUMNS = 4

/** How many words the canvas puts in a column. */
private const val ROWS = 4

/** A column of the canvas: its title's line and gap ([ColumnTitle]) and [ROWS] words at line height 2. */
private fun columnHeight(compact: Boolean) = if (compact) (28 + 12 + 28 * ROWS).dp else (30 + 16 + 30 * ROWS).dp

/**
 * A column's title: an inline 11 px label in a block whose line is the footer's (15 px at line height
 * 2), so it sits on that line's baseline, 30 px tall, as the browser draws it.
 */
@Composable
private fun ColumnTitle(
    title: String,
    compact: Boolean,
) {
    InlineLine(
        title.uppercase(),
        HaulType.label(11f, 600, 0.08f).copy(color = HaulColors.secondaryContainer),
        strut = HaulType.text(if (compact) 14f else 15f, lineHeight = 2f),
        modifier = Modifier.padding(bottom = if (compact) 12.dp else 16.dp),
    )
}
