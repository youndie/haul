package io.github.youndie.haul.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
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

/**
 * Whether the desktop page is narrower than its arrangement at 1440 holds (B-82): from 1024 to 1279 px no
 * artboard draws, and what the 1440 arrangement squeezes there is rearranged — the header's search field on a
 * row of its own, a cart line drawn as the phone's, the product's buy box under its title, the campaign's
 * figures and the banners' titles smaller. Never set together with [LocalHaulCompact].
 */
public val LocalHaulMedium: androidx.compose.runtime.ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf { false }

/** The theme: [fonts], the width's arrangement, and [colors] — the canvas's light scheme unless one is chosen (B-180). */
@Composable
public fun HaulTheme(
    fonts: HaulFonts,
    compact: Boolean,
    medium: Boolean = false,
    colors: HaulColorScheme = HaulColorScheme.Light,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalHaulColorScheme provides colors,
        LocalTextSelectionColors provides TextSelectionColors(colors.selectionHandle, colors.selectionBackground),
        LocalHaulFonts provides fonts,
        LocalHaulCompact provides compact,
        LocalHaulMedium provides (medium && !compact),
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
    ): TextStyle =
        style(
            LocalHaulFonts.current,
            LocalHaulFonts.current.archivo,
            size,
            weight,
            lineHeight?.let {
                (
                    size *
                        it
                ).sp
            },
            HaulColors.onSurface,
        )

    /**
     * Bodoni Moda — headlines, prices, the logo; the optical size follows the pixel size. The app's text
     * draws its figures and punctuation through [legibleFigures].
     */
    @Composable
    public fun display(
        size: Float,
        weight: Int,
        letterSpacing: Float = 0f,
        lineHeight: Float = 1f,
    ): TextStyle = display(LocalHaulFonts.current, size, weight, letterSpacing, lineHeight, HaulColors.onSurface)

    /**
     * [display] in [fonts]: for a size chosen while measuring, where no composition locals are read (B-82). Without a
     * [color] the text is drawn in Ink of the scheme being drawn (`Text` reads it, B-180).
     */
    public fun display(
        fonts: HaulFonts,
        size: Float,
        weight: Int,
        letterSpacing: Float = 0f,
        lineHeight: Float = 1f,
        color: Color = Color.Unspecified,
    ): TextStyle =
        style(
            fonts,
            fonts.bodoni(size.coerceIn(BODONI_OPSZ_MIN, BODONI_OPSZ_MAX)),
            size,
            weight,
            (
                size *
                    lineHeight
            ).sp,
            color,
        ).copy(letterSpacing = letterSpacing.em)

    /**
     * [text] as a [display] [style] draws it legibly: its figures and punctuation — every character that is
     * not a letter or a space — at [legibleOpticalSize] instead of the canvas's optical size, the letters
     * as the canvas draws them. At the canvas's size the face's hairlines are 0.2 px: a hyphen and a dash
     * vanish («WH 1000XM6», «#HL 48303») and a 4 loses its diagonal and bar and reads as a 1 («$31.19»,
     * «1.8»), B-106. A figure's and a mark's advance is the same at every optical size (a «#» or a «/»
     * moves by half a pixel at most), so no line breaks elsewhere; a letter's is not, which is why the
     * letters keep the canvas's cut. A character a span already gives a family of its own keeps it; a
     * [style] that is not Bodoni Moda's leaves [text] as it is.
     */
    @Composable
    public fun legibleFigures(
        text: AnnotatedString,
        style: TextStyle,
    ): AnnotatedString {
        val fonts = LocalHaulFonts.current
        val size = style.fontSize
        if (!size.isSp || style.fontFamily != fonts.bodoni(size.value.coerceIn(BODONI_OPSZ_MIN, BODONI_OPSZ_MAX))) {
            return text
        }
        val figures = fonts.bodoni(legibleOpticalSize(size.value))
        return remember(text, figures) {
            val chosen = text.spanStyles.filter { it.item.fontFamily != null }

            fun isFigure(index: Int): Boolean =
                text[index].let { !it.isLetter() && !it.isWhitespace() } &&
                    chosen.none { index in it.start until it.end }
            buildAnnotatedString {
                append(text)
                var start = -1
                for (index in 0..text.length) {
                    val figure = index < text.length && isFigure(index)
                    if (figure && start < 0) start = index
                    if (!figure && start >= 0) {
                        addStyle(SpanStyle(fontFamily = figures), start, index)
                        start = -1
                    }
                }
            }
        }
    }

    private val BROWSER_LEADING = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

    /** JetBrains Mono — labels, counts, delivery days; uppercase is the caller's, as on the canvas. */
    @Composable
    public fun label(
        size: Float,
        weight: Int,
        letterSpacing: Float,
    ): TextStyle =
        style(
            LocalHaulFonts.current,
            LocalHaulFonts.current.mono,
            size,
            weight,
            size.sp,
            HaulColors.onSurface,
        ).copy(letterSpacing = letterSpacing.em)

    private fun style(
        fonts: HaulFonts,
        family: FontFamily,
        size: Float,
        weight: Int,
        lineHeight: TextUnit?,
        color: Color,
    ): TextStyle =
        TextStyle(
            fontFamily = family,
            fontSize = size.sp,
            fontWeight = FontWeight(weight),
            lineHeight = lineHeight ?: TextUnit.Unspecified,
            // A set line height is laid out as CSS does: the leading split evenly, nothing trimmed.
            lineHeightStyle = if (lineHeight != null) BROWSER_LEADING else null,
            color = color,
            platformStyle = fonts.platformStyle,
        )

    private const val BODONI_OPSZ_MIN = 6f
    private const val BODONI_OPSZ_MAX = 96f
}
