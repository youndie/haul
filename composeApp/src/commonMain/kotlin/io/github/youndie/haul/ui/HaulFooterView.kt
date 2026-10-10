package io.github.youndie.haul.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.HaulType.browserLeading
import io.github.youndie.haul.theme.LocalHaulCompact

/**
 * The footer (`HaulFooter` on the wire), with the gap the page leaves above it: up to four link columns and
 * «Get the app» at 1440, two columns a row and no app block at 390, the wordmark cut off by the page's end.
 * A word follows its entry in `FooterColumn.linked` (B-72); one without is drawn as text, nothing to press.
 * At 1440 the columns keep the canvas's four places, so «Get the app» stays at the end with fewer of them.
 */
@Composable
public fun HaulFooterView(footer: HaulFooter) {
    val compact = LocalHaulCompact.current
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(if (compact) 64.dp else 80.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(HaulColors.inverseSurface)
                .clipToBounds()
                .padding(
                    start = if (compact) 16.dp else 48.dp,
                    end = if (compact) 16.dp else 48.dp,
                    top = if (compact) 40.dp else 64.dp,
                ),
        ) {
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    footer.columns.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            pair.forEach { LinkColumn(it, compact = true, Modifier.weight(1f)) }
                            repeat(2 - pair.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    footer.columns.forEach { LinkColumn(it, compact = false, Modifier.weight(1f)) }
                    repeat(COLUMNS - footer.columns.size) { Box(Modifier.weight(1f)) }
                    Column(Modifier.weight(1.4f)) {
                        ColumnTitle(footer.appTitle, compact = false)
                        Text(
                            footer.appText,
                            HaulType
                                .text(
                                    15f,
                                    lineHeight = 1.5f,
                                ).browserLeading()
                                .copy(color = HaulColors.inverseOnSurfaceVariant),
                            Modifier.widthIn(max = 280.dp),
                        )
                    }
                }
            }
            val size = if (compact) 150f else 420f
            Text(
                buildAnnotatedString {
                    append("Haul")
                    withStyle(SpanStyle(color = HaulColors.primary)) { append(".") }
                },
                Modifier
                    .padding(top = if (compact) 40.dp else 56.dp)
                    .offset(x = if (compact) (-6).dp else (-14).dp)
                    .negativeBottomMargin(if (compact) 24.dp else 70.dp),
                style =
                    HaulType
                        .display(size, 900, letterSpacing = -0.05f, lineHeight = 0.78f)
                        .copy(color = HaulColors.secondaryContainer),
                softWrap = false,
            )
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
            ).browserLeading()
            .copy(color = HaulColors.inverseOnSurface)
    Column(modifier) {
        ColumnTitle(column.title, compact)
        column.links.forEach { word ->
            Text(word, style, Modifier.follows(column.linked.firstOrNull { it.label == word }?.action))
        }
    }
}

/** How many link columns the canvas lays out at 1440. */
private const val COLUMNS = 4

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
