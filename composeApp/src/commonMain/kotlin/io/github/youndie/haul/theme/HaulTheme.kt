package io.github.youndie.haul.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.youndie.kompot.LocalKompotDesignSystem

/**
 * The canvas's three families. Bodoni Moda is asked for per optical size, because the canvas draws it
 * with `font-optical-sizing: auto` — a 44 px logo and a 22 px price are different cuts of one font.
 */
@Immutable
public class HaulFonts(
    public val archivo: FontFamily,
    public val mono: FontFamily,
    private val bodoniAt: (opticalSize: Float) -> FontFamily,
    /** Rasterisation pinned by the screenshot fixtures; `null` in the app. */
    public val platformStyle: PlatformTextStyle? = null,
) {
    public fun bodoni(opticalSize: Float): FontFamily = bodoniAt(opticalSize)
}

public val LocalHaulFonts: androidx.compose.runtime.ProvidableCompositionLocal<HaulFonts> =
    staticCompositionLocalOf { error("HaulTheme is not applied: no fonts") }

/** Whether the page is drawn at the phone width (the canvas's 390) rather than the desktop one. */
public val LocalHaulCompact: androidx.compose.runtime.ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf { false }

@Composable
public fun HaulTheme(
    fonts: HaulFonts,
    compact: Boolean,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalHaulFonts provides fonts,
        LocalHaulCompact provides compact,
        LocalKompotDesignSystem provides HaulDesignSystem,
        content = content,
    )
}

/** The type roles of `canvas.json` (`typeRoles`), each built at the exact size an artboard uses. */
public object HaulType {
    /** Archivo — UI text. */
    @Composable
    public fun text(
        size: Float,
        weight: Int = 400,
        lineHeight: Float? = null,
    ): TextStyle = style(LocalHaulFonts.current.archivo, size, weight, lineHeight?.let { (size * it).sp })

    /** Bodoni Moda — headlines, prices, the logo; the optical size follows the pixel size. */
    @Composable
    public fun display(
        size: Float,
        weight: Int,
        letterSpacing: Float = 0f,
        lineHeight: Float = 1f,
    ): TextStyle =
        style(
            LocalHaulFonts.current.bodoni(size.coerceIn(BODONI_OPSZ_MIN, BODONI_OPSZ_MAX)),
            size,
            weight,
            (
                size *
                    lineHeight
            ).sp,
        ).copy(letterSpacing = letterSpacing.em)

    /** CSS's leading: the space a line height adds (or takes) split evenly above and below, nothing trimmed. */
    public fun TextStyle.browserLeading(): TextStyle = copy(lineHeightStyle = BROWSER_LEADING)

    private val BROWSER_LEADING = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

    /** JetBrains Mono — labels, counts, delivery days; uppercase is the caller's, as on the canvas. */
    @Composable
    public fun label(
        size: Float,
        weight: Int,
        letterSpacing: Float,
    ): TextStyle = style(LocalHaulFonts.current.mono, size, weight, size.sp).copy(letterSpacing = letterSpacing.em)

    @Composable
    private fun style(
        family: FontFamily,
        size: Float,
        weight: Int,
        lineHeight: TextUnit?,
    ): TextStyle =
        TextStyle(
            fontFamily = family,
            fontSize = size.sp,
            fontWeight = FontWeight(weight),
            lineHeight = lineHeight ?: TextUnit.Unspecified,
            // A set line height is laid out as CSS does: the leading split evenly, nothing trimmed.
            lineHeightStyle = if (lineHeight != null) BROWSER_LEADING else null,
            color = HaulColors.onSurface,
            platformStyle = LocalHaulFonts.current.platformStyle,
        )

    private const val BODONI_OPSZ_MIN = 6f
    private const val BODONI_OPSZ_MAX = 96f
}
