package io.github.youndie.haul.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.KompotDesignSystem
import io.github.youndie.kompot.TypographyToken

/**
 * kompot's tokens resolved to the canvas's roles, for the standard components a tree is laid out with
 * (`column`, `row`, `text`). A colour token is a role's name (`primary`, `onSurface`, …), resolved in the scheme
 * being drawn; anything else is Ink. The Haul components draw with [HaulColors] and [HaulType] directly and never ask.
 */
internal object HaulDesignSystem : KompotDesignSystem {
    private val roles: Map<String, (HaulColorScheme) -> Color> =
        mapOf(
            "primary" to { it.primary },
            "onPrimary" to { it.onPrimary },
            "secondaryContainer" to { it.secondaryContainer },
            "onSecondaryContainer" to { it.onSecondaryContainer },
            "tertiaryContainer" to { it.tertiaryContainer },
            "error" to { it.error },
            "errorContainer" to { it.errorContainer },
            "background" to { it.background },
            "surface" to { it.background },
            "surfaceContainerLowest" to { it.surfaceContainerLowest },
            "surfaceContainerHigh" to { it.surfaceContainerHigh },
            "onSurface" to { it.onSurface },
            "onSurfaceVariant" to { it.onSurfaceVariant },
            "outline" to { it.outline },
            "outlineVariant" to { it.outlineVariant },
            "inverseSurface" to { it.inverseSurface },
            "inverseOnSurface" to { it.inverseOnSurface },
        )

    /** A role of the scheme being drawn (B-180): a tree that names `primary` gets the dark page's on a dark page. */
    @Composable
    override fun resolveColor(token: ColorToken): Color =
        HaulColors.scheme.let { scheme -> roles[token.key]?.invoke(scheme) ?: scheme.onSurface }

    /** The canvas's body copy: Archivo 15 px. */
    @Composable
    override fun resolveTypography(token: TypographyToken): TextStyle = HaulType.text(15f)
}
