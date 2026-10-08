package io.github.youndie.haul

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.FontHinting
import androidx.compose.ui.text.FontRasterizationSettings
import androidx.compose.ui.text.FontSmoothing
import androidx.compose.ui.text.PlatformParagraphStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import io.github.youndie.haul.theme.HaulFonts
import io.github.youndie.viddik.core.normalizeVerticalMetrics
import java.io.File

/**
 * The app's three font files, read the way a golden needs them: vertical metrics normalised and the
 * rasterisation pinned, so a golden recorded on one operating system holds on another. The files
 * are the ones the app ships (`composeResources/font`), not copies.
 */
internal object FixtureFonts {
    /**
     * viddik's pinned rasterisation (no hinting, anti-aliased) with one change: glyphs are placed at
     * fractional positions, as a browser places them. With whole-pixel positions every advance rounds
     * up — JetBrains Mono's 6.6 px becomes 7 — and a line of text ends a few pixels to the right of
     * where the canvas ends it, which is most of what a design comparison then measures (B-07).
     */
    @OptIn(ExperimentalTextApi::class)
    private val BROWSER_RASTERISATION =
        PlatformTextStyle(
            spanStyle = null,
            paragraphStyle =
                PlatformParagraphStyle(
                    fontRasterizationSettings =
                        FontRasterizationSettings(
                            smoothing = FontSmoothing.AntiAlias,
                            hinting = FontHinting.None,
                            subpixelPositioning = true,
                            autoHintingForced = false,
                        ),
                ),
        )

    private fun bytes(name: String): ByteArray =
        normalizeVerticalMetrics(File("src/commonMain/composeResources/font/$name").readBytes())

    private val archivo by lazy { bytes("archivo.ttf") }
    private val mono by lazy { bytes("jetbrains_mono.ttf") }
    private val bodoni by lazy { bytes("bodoni_moda.ttf") }
    private val bodoniItalic by lazy { bytes("bodoni_moda_italic.ttf") }

    private fun family(
        name: String,
        data: ByteArray,
        weights: List<Int>,
        vararg extra: FontVariation.Setting,
    ): FontFamily = FontFamily(fonts(name, data, weights, FontStyle.Normal, *extra))

    private fun fonts(
        name: String,
        data: ByteArray,
        weights: List<Int>,
        style: FontStyle,
        vararg extra: FontVariation.Setting,
    ): List<androidx.compose.ui.text.font.Font> =
        weights.map {
            Font(
                identity = "$name-$it-$style-${extra.joinToString { s -> s.toString() }}",
                data = data,
                weight = FontWeight(it),
                style = style,
                variationSettings = FontVariation.Settings(FontVariation.weight(it), *extra),
            )
        }

    val fonts: HaulFonts by lazy {
        val bodoniCache = mutableMapOf<Float, FontFamily>()
        HaulFonts(
            archivo = family("archivo", archivo, listOf(400, 500, 600, 700, 800)),
            mono = family("mono", mono, listOf(500, 600)),
            bodoniAt = { opsz ->
                bodoniCache.getOrPut(
                    opsz,
                ) {
                    val size = FontVariation.Setting("opsz", opsz)
                    FontFamily(
                        fonts("bodoni", bodoni, listOf(700, 800, 900), FontStyle.Normal, size) +
                            fonts("bodoni-italic", bodoniItalic, listOf(500), FontStyle.Italic, size),
                    )
                }
            },
            platformStyle = BROWSER_RASTERISATION,
        )
    }
}
