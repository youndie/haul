package io.github.youndie.haul.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.KompotDesignSystem
import io.github.youndie.kompot.TypographyToken

/**
 * kompot's tokens resolved to the canvas's roles, for the standard components a tree is laid out with
 * (`column`, `row`, `text`). A colour token is a role's name (`primary`, `onSurface`, …); anything else
 * is Ink. The Haul components draw with [HaulColors] and [HaulType] directly and never ask.
 */
internal object HaulDesignSystem : KompotDesignSystem {
    private val roles: Map<String, Color> =
        mapOf(
            "primary" to HaulColors.primary,
            "onPrimary" to HaulColors.onPrimary,
            "secondaryContainer" to HaulColors.secondaryContainer,
            "onSecondaryContainer" to HaulColors.onSecondaryContainer,
            "tertiaryContainer" to HaulColors.tertiaryContainer,
            "error" to HaulColors.error,
            "errorContainer" to HaulColors.errorContainer,
            "background" to HaulColors.background,
            "surface" to HaulColors.background,
            "surfaceContainerLowest" to HaulColors.surfaceContainerLowest,
            "surfaceContainerHigh" to HaulColors.surfaceContainerHigh,
            "onSurface" to HaulColors.onSurface,
            "onSurfaceVariant" to HaulColors.onSurfaceVariant,
            "outline" to HaulColors.outline,
            "outlineVariant" to HaulColors.outlineVariant,
            "inverseSurface" to HaulColors.inverseSurface,
            "inverseOnSurface" to HaulColors.inverseOnSurface,
        )

    @Composable
    override fun resolveColor(token: ColorToken): Color = roles[token.key] ?: HaulColors.onSurface

    /** The canvas's body copy: Archivo 15 px. */
    @Composable
    override fun resolveTypography(token: TypographyToken): TextStyle = HaulType.text(15f)
}
