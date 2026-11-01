package io.github.youndie.haul.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The canvas's colour roles (`canvas/canvas.json`, `roles`), named as the canvas names them, in one scheme: [Light] is
 * the canvas's, [Dark] the store's own (B-180 — the canvas draws no dark artboard, and since B-85 the code is the
 * reference; the table and its contrasts are in the research, D7, «Decided in B-180»). A colour a renderer needs that
 * is not here is a role to add to both schemes, not a literal in the renderer (`HardCodedColourTest`).
 *
 * The roles beyond the canvas's are the store's: the band ([band] and what is drawn on it) is the footer's, the
 * header's strip's, the Plus offer's and the messages' dark ground, which stays dark in both schemes because its
 * Acid words read on nothing light; [inverseSurface] is Material's, the fill of a chosen chip, the cart button, the
 * other strong fills — Ink on Paper, Paper on Ink in the dark.
 */
@Immutable
public class HaulColorScheme(
    /** Whether this is the dark scheme: what the page's frame, the window and the pictures (B-181) follow. */
    public val isDark: Boolean,
    /** Cobalt: actions, links, selection. */
    public val primary: Color,
    public val onPrimary: Color,
    /**
     * Cobalt as a ground in both schemes (Material's «fixed» role): the campaign's and the membership tile's, under
     * white words and Acid ones, which a lifted dark [primary] would not hold.
     */
    public val primaryFixed: Color,
    public val onPrimaryFixed: Color,
    /** Acid: discount badges, highlights, counts. */
    public val secondaryContainer: Color,
    public val onSecondaryContainer: Color,
    /** Hot: promo fills, the saved heart, «Price dropped». */
    public val tertiaryContainer: Color,
    /** Sale red: «Deals», discount amounts, field errors. */
    public val error: Color,
    public val errorContainer: Color,
    /** Paper: the page. */
    public val background: Color,
    public val surfaceContainerLowest: Color,
    public val surfaceContainerHigh: Color,
    public val surfaceContainerHighest: Color,
    /** Ink: text and icons. */
    public val onSurface: Color,
    public val onSurfaceVariant: Color,
    public val outline: Color,
    public val outlineMuted: Color,
    public val outlineVariant: Color,
    public val outlineControl: Color,
    public val inverseSurface: Color,
    public val inverseOnSurface: Color,
    public val inverseOnSurfaceVariant: Color,
    /**
     * The accent on [inverseSurface]: Acid on Ink, as the canvas draws a countdown and a chosen day; Cobalt on the dark
     * scheme's Paper, where Acid would not read.
     */
    public val inverseAccent: Color,
    /** The band: a dark ground in both schemes, for the Acid words (the footer, the strip, the Plus offer, messages). */
    public val band: Color,
    /** Words on the band: Paper. */
    public val onBand: Color,
    /** Quieter words on the band. */
    public val onBandVariant: Color,
    /** The band's brightest words and icons: white, as the canvas draws the Plus dialog's title and the campaign's. */
    public val onBandStrong: Color,
    /** A control on the band: white at 12 % (the Plus trial dialog's «×», `Home_PlusTrialDialog`). */
    public val bandControl: Color,
    /** The campaign's hatching, white at 9 %, over its Cobalt in both schemes. */
    public val bandHatch: Color,
    public val scrim: Color,
    /**
     * A floating panel's shadow, black at 45 % (Search_Autocomplete's `box-shadow`). `canvas.json` lists no role for
     * it: the canvas's own shadows are written inline.
     */
    public val shadow: Color,
    /** A dialog card's shadow, black at 50 % (the Plus trial's and the review's dialogs). */
    public val dialogShadow: Color,
    /** The placeholder photo tile's hatching: Ink at 5 %. */
    public val tileHatch: Color,
    /** The banner's placeholder tile's hatching: Ink at 8 %. */
    public val bannerHatch: Color,
    /** The light that sweeps a placeholder while it loads (B-158): white at 55 %. */
    public val shimmer: Color,
    /** A switch's knob: white on its track, as the canvas draws the filter panel's toggle. */
    public val knob: Color,
    /** Selected words' handles (B-154): light, Compose's own default, which the light goldens were drawn with. */
    public val selectionHandle: Color,
    /** Selected words' ground. */
    public val selectionBackground: Color,
    /** The canvas's tile tones the server sends (`#RRGGBB`), each to the colour it is drawn in under this scheme. */
    private val tones: Map<String, Color>,
    /** The tile a tone that is not `#RRGGBB` is drawn in: the canvas's stone. */
    private val stone: Color,
) {
    /**
     * The tile a tree's [tone] is drawn in. The server names a tile by the canvas's tone, `#RRGGBB`, as a light page
     * draws it; a scheme draws each of the canvas's tones as its counterpart (the dark ones in the research), and a
     * tone it has none for — the campaign's Cobalt — as sent. Not for a product's colour swatch: that is the product's.
     */
    public fun tone(tone: String): Color {
        val key = tone.removePrefix("#").uppercase()
        return tones[key] ?: hexColor(tone) ?: stone
    }

    /**
     * The words drawn on a tile of [tone]: the canvas's Ink on a light tile — every tone of a light page, and the Acid a
     * Plus banner and a member's avatar keep on a dark one — and the dark scheme's Paper on a dark tile.
     */
    public fun onTone(tone: String): Color =
        if (tone(tone).luminance() > LIGHT_TILE) Light.onSurface else Dark.onSurface

    public companion object {
        /** A tile lighter than this takes Ink; the canvas's tones are all above it, their dark counterparts below. */
        private const val LIGHT_TILE = 0.4f

        /** The canvas's: every value as `canvas.json` and the light goldens draw it. */
        public val Light: HaulColorScheme =
            HaulColorScheme(
                isDark = false,
                primary = Color(0xFF2F2BFF),
                onPrimary = Color(0xFFFFFFFF),
                primaryFixed = Color(0xFF2F2BFF),
                onPrimaryFixed = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFDFFF3A),
                onSecondaryContainer = Color(0xFF0F0F0F),
                tertiaryContainer = Color(0xFFFF3D2E),
                error = Color(0xFFD9230F),
                errorContainer = Color(0xFFFFE5DD),
                background = Color(0xFFF5F3EE),
                surfaceContainerLowest = Color(0xFFFFFFFF),
                surfaceContainerHigh = Color(0xFFE9E6E0),
                surfaceContainerHighest = Color(0xFFD6D1C7),
                onSurface = Color(0xFF0F0F0F),
                onSurfaceVariant = Color(0xFF3A3833),
                outline = Color(0xFF5E5B55),
                outlineMuted = Color(0xFF6F6B64),
                outlineVariant = Color(0xFFE2DED6),
                outlineControl = Color(0xFFC9C4BA),
                inverseSurface = Color(0xFF0F0F0F),
                inverseOnSurface = Color(0xFFF5F3EE),
                inverseOnSurfaceVariant = Color(0xFFBDB8AE),
                inverseAccent = Color(0xFFDFFF3A),
                band = Color(0xFF0F0F0F),
                onBand = Color(0xFFF5F3EE),
                onBandVariant = Color(0xFFBDB8AE),
                onBandStrong = Color(0xFFFFFFFF),
                bandControl = Color(0x1FFFFFFF),
                bandHatch = Color(0x17FFFFFF),
                scrim = Color(0x800F0F0F),
                shadow = Color(0x73000000),
                dialogShadow = Color(0x80000000),
                tileHatch = Color(0x0D0F0F0F),
                bannerHatch = Color(0x140F0F0F),
                shimmer = Color(0x8CFFFFFF),
                knob = Color(0xFFFFFFFF),
                selectionHandle = Color(0xFF4286F4),
                selectionBackground = Color(0x664286F4),
                tones = emptyMap(),
                stone = Color(0xFFECE9E2),
            )

        /**
         * The store's dark scheme (B-180): warm near-black paper, the canvas's ink turned to paper, Cobalt lifted to a
         * lavender that reads on the dark page with Ink on it, Acid and Hot as they are; every pair of words and
         * ground at least the light one's contrast, or 7:1 where the light one is above that (research D7).
         */
        public val Dark: HaulColorScheme =
            HaulColorScheme(
                isDark = true,
                primary = Color(0xFFA3A0FF),
                onPrimary = Color(0xFF0F0F0F),
                primaryFixed = Color(0xFF2F2BFF),
                onPrimaryFixed = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFDFFF3A),
                onSecondaryContainer = Color(0xFF0F0F0F),
                tertiaryContainer = Color(0xFFFF3D2E),
                error = Color(0xFFFF7461),
                errorContainer = Color(0xFF3D1D16),
                background = Color(0xFF141311),
                surfaceContainerLowest = Color(0xFF211F1C),
                surfaceContainerHigh = Color(0xFF2A2824),
                surfaceContainerHighest = Color(0xFF3B3833),
                onSurface = Color(0xFFF2EFE8),
                onSurfaceVariant = Color(0xFFD2CDC3),
                outline = Color(0xFFABA69C),
                outlineMuted = Color(0xFF9A958B),
                outlineVariant = Color(0xFF3A3731),
                outlineControl = Color(0xFF5C5850),
                inverseSurface = Color(0xFFF2EFE8),
                inverseOnSurface = Color(0xFF0F0F0F),
                inverseOnSurfaceVariant = Color(0xFF4A4741),
                inverseAccent = Color(0xFF2F2BFF),
                band = Color(0xFF2B2925),
                onBand = Color(0xFFF5F3EE),
                onBandVariant = Color(0xFFC3BEB4),
                onBandStrong = Color(0xFFFFFFFF),
                bandControl = Color(0x1FFFFFFF),
                bandHatch = Color(0x17FFFFFF),
                scrim = Color(0xB3000000),
                shadow = Color(0x99000000),
                dialogShadow = Color(0xB3000000),
                tileHatch = Color(0x0FF5F3EE),
                bannerHatch = Color(0x14F5F3EE),
                shimmer = Color(0x14FFFFFF),
                knob = Color(0xFFF2EFE8),
                selectionHandle = Color(0xFFA3A0FF),
                selectionBackground = Color(0x66A3A0FF),
                tones =
                    mapOf(
                        "E6E4FF" to Color(0xFF1D1B39),
                        "F1EBDD" to Color(0xFF302D24),
                        "FFE5DD" to Color(0xFF39221B),
                        "F1E4F5" to Color(0xFF2E2331),
                        "FFF1C9" to Color(0xFF39311B),
                        "E3F5D8" to Color(0xFF283321),
                        "E9F0D2" to Color(0xFF2E3223),
                        "E0EEF7" to Color(0xFF212C33),
                        "ECE9E2" to Color(0xFF2D2B27),
                    ),
                stone = Color(0xFF2D2B27),
            )
    }
}

/** The scheme the page is drawn in; the canvas's light one where nothing chose (a view drawn on its own, a golden). */
public val LocalHaulColorScheme: ProvidableCompositionLocal<HaulColorScheme> =
    staticCompositionLocalOf { HaulColorScheme.Light }

/** `#RRGGBB` as a colour — a product's swatch, as its seed names it — or `null` when it is not one. */
public fun hexColor(hex: String): Color? =
    hex
        .removePrefix("#")
        .takeIf { it.length == 6 }
        ?.toLongOrNull(16)
        ?.let { Color(0xFF000000 or it) }

/**
 * The roles of the scheme being drawn ([LocalHaulColorScheme]), by the canvas's names: `HaulColors.primary` is the
 * light Cobalt on a light page and the dark scheme's on a dark one. Read in a composition; a drawing lambda reads the
 * colour into a value first.
 */
public object HaulColors {
    public val scheme: HaulColorScheme
        @Composable @ReadOnlyComposable
        get() = LocalHaulColorScheme.current

    public val primary: Color
        @Composable @ReadOnlyComposable
        get() = scheme.primary
    public val onPrimary: Color
        @Composable @ReadOnlyComposable
        get() = scheme.onPrimary
    public val primaryFixed: Color
        @Composable @ReadOnlyComposable
        get() = scheme.primaryFixed
    public val onPrimaryFixed: Color
        @Composable @ReadOnlyComposable
        get() = scheme.onPrimaryFixed
    public val secondaryContainer: Color
        @Composable @ReadOnlyComposable
        get() = scheme.secondaryContainer
    public val onSecondaryContainer: Color
        @Composable @ReadOnlyComposable
        get() = scheme.onSecondaryContainer
    public val tertiaryContainer: Color
        @Composable @ReadOnlyComposable
        get() = scheme.tertiaryContainer
    public val error: Color
        @Composable @ReadOnlyComposable
        get() = scheme.error
    public val errorContainer: Color
        @Composable @ReadOnlyComposable
        get() = scheme.errorContainer
    public val background: Color
        @Composable @ReadOnlyComposable
        get() = scheme.background
    public val surfaceContainerLowest: Color
        @Composable @ReadOnlyComposable
        get() = scheme.surfaceContainerLowest
    public val surfaceContainerHigh: Color
        @Composable @ReadOnlyComposable
        get() = scheme.surfaceContainerHigh
    public val surfaceContainerHighest: Color
        @Composable @ReadOnlyComposable
        get() = scheme.surfaceContainerHighest
    public val onSurface: Color
        @Composable @ReadOnlyComposable
        get() = scheme.onSurface
    public val onSurfaceVariant: Color
        @Composable @ReadOnlyComposable
        get() = scheme.onSurfaceVariant
    public val outline: Color
        @Composable @ReadOnlyComposable
        get() = scheme.outline
    public val outlineMuted: Color
        @Composable @ReadOnlyComposable
        get() = scheme.outlineMuted
    public val outlineVariant: Color
        @Composable @ReadOnlyComposable
        get() = scheme.outlineVariant
    public val outlineControl: Color
        @Composable @ReadOnlyComposable
        get() = scheme.outlineControl
    public val inverseSurface: Color
        @Composable @ReadOnlyComposable
        get() = scheme.inverseSurface
    public val inverseOnSurface: Color
        @Composable @ReadOnlyComposable
        get() = scheme.inverseOnSurface
    public val inverseOnSurfaceVariant: Color
        @Composable @ReadOnlyComposable
        get() = scheme.inverseOnSurfaceVariant
    public val inverseAccent: Color
        @Composable @ReadOnlyComposable
        get() = scheme.inverseAccent
    public val band: Color
        @Composable @ReadOnlyComposable
        get() = scheme.band
    public val onBand: Color
        @Composable @ReadOnlyComposable
        get() = scheme.onBand
    public val onBandVariant: Color
        @Composable @ReadOnlyComposable
        get() = scheme.onBandVariant
    public val onBandStrong: Color
        @Composable @ReadOnlyComposable
        get() = scheme.onBandStrong
    public val bandControl: Color
        @Composable @ReadOnlyComposable
        get() = scheme.bandControl
    public val bandHatch: Color
        @Composable @ReadOnlyComposable
        get() = scheme.bandHatch
    public val scrim: Color
        @Composable @ReadOnlyComposable
        get() = scheme.scrim
    public val shadow: Color
        @Composable @ReadOnlyComposable
        get() = scheme.shadow
    public val dialogShadow: Color
        @Composable @ReadOnlyComposable
        get() = scheme.dialogShadow
    public val tileHatch: Color
        @Composable @ReadOnlyComposable
        get() = scheme.tileHatch
    public val bannerHatch: Color
        @Composable @ReadOnlyComposable
        get() = scheme.bannerHatch
    public val shimmer: Color
        @Composable @ReadOnlyComposable
        get() = scheme.shimmer
    public val knob: Color
        @Composable @ReadOnlyComposable
        get() = scheme.knob

    /** The tile a tree's tone is drawn in ([HaulColorScheme.tone]). */
    @Composable
    @ReadOnlyComposable
    public fun tone(tone: String): Color = scheme.tone(tone)

    /** The words on a tile of a tree's tone ([HaulColorScheme.onTone]). */
    @Composable
    @ReadOnlyComposable
    public fun onTone(tone: String): Color = scheme.onTone(tone)
}
