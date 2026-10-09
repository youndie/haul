package io.github.youndie.haul.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.HaulType.browserLeading
import io.github.youndie.haul.theme.LocalHaulCompact

/**
 * An empty or no-results state (`EmptyState` on the wire): the title, balanced as the canvas balances
 * it (`text-wrap: balance`), the sentence when there is one, and the way out — outlined, or filled in Cobalt when it is
 * the page's [EmptyState.primary] one.
 */
@Composable
public fun EmptyStateView(
    empty: EmptyState,
    modifier: Modifier = Modifier,
) {
    val compact = LocalHaulCompact.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 18.dp else 24.dp)) {
        BalancedText(
            accented(empty.title, empty.accent),
            modifier = Modifier.widthIn(max = 900.dp),
            style =
                HaulType.display(
                    if (compact) 44f else 84f,
                    800,
                    letterSpacing = if (compact) -0.01f else -0.03f,
                    lineHeight = 0.95f,
                ),
        )
        empty.text?.let {
            Text(
                it,
                HaulType
                    .text(
                        if (compact) 16f else 18f,
                        lineHeight = 1.5f,
                    ).browserLeading()
                    .copy(color = HaulColors.onSurfaceVariant),
                Modifier.widthIn(max = 560.dp),
            )
        }
        empty.actionLabel?.let {
            HaulButton(
                it,
                height = if (compact) 56.dp else 64.dp,
                radius = 18.dp,
                fill = if (empty.primary) HaulColors.primary else null,
                content = if (empty.primary) HaulColors.onPrimary else HaulColors.onSurface,
                border = if (empty.primary) null else HaulColors.onSurface,
                icon = HaulIcons.arrowRight,
                onClick = following(empty.action),
            )
        }
    }
}
