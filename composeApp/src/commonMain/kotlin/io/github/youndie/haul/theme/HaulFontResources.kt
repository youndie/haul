package io.github.youndie.haul.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.youndie.haul.resources.Res
import io.github.youndie.haul.resources.archivo
import io.github.youndie.haul.resources.bodoni_moda
import io.github.youndie.haul.resources.bodoni_moda_italic
import io.github.youndie.haul.resources.jetbrains_mono
import org.jetbrains.compose.resources.Font

/** The weights the canvas uses, per family; a variable font is asked for one weight at a time. */
private val ARCHIVO_WEIGHTS = listOf(400, 500, 600, 700, 800)
private val MONO_WEIGHTS = listOf(500, 600)
private val BODONI_WEIGHTS = listOf(700, 800, 900)

/** The canvas draws Bodoni's italic only for a title's accent, and only at 500. */
private val BODONI_ITALIC_WEIGHTS = listOf(500)

/** The app's fonts, from the bundled variable files (OFL, `files/licences`). */
@Composable
public fun rememberHaulFonts(): HaulFonts {
    val archivo =
        FontFamily(
            ARCHIVO_WEIGHTS.map {
                Font(
                    Res.font.archivo,
                    FontWeight(it),
                    variationSettings = FontVariation.Settings(FontVariation.weight(it)),
                )
            },
        )
    val mono =
        FontFamily(
            MONO_WEIGHTS.map {
                Font(
                    Res.font.jetbrains_mono,
                    FontWeight(it),
                    variationSettings = FontVariation.Settings(FontVariation.weight(it)),
                )
            },
        )
    val bodoni =
        BODONI_OPTICAL_SIZES.associateWith { opsz ->
            FontFamily(
                BODONI_WEIGHTS.map {
                    Font(
                        Res.font.bodoni_moda,
                        FontWeight(it),
                        variationSettings =
                            FontVariation.Settings(
                                FontVariation.weight(it),
                                FontVariation.Setting("opsz", opsz),
                            ),
                    )
                } +
                    BODONI_ITALIC_WEIGHTS.map {
                        Font(
                            Res.font.bodoni_moda_italic,
                            FontWeight(it),
                            FontStyle.Italic,
                            variationSettings =
                                FontVariation.Settings(
                                    FontVariation.weight(it),
                                    FontVariation.Setting("opsz", opsz),
                                ),
                        )
                    },
            )
        }
    return HaulFonts(archivo, mono, { size -> bodoni.getValue(nearestOpticalSize(size)) })
}

/** The optical sizes the canvas draws Bodoni Moda in. */
private val CANVAS_OPTICAL_SIZES = listOf(22f, 24f, 26f, 28f, 30f, 34f, 40f, 44f, 48f, 56f, 72f, 84f, 96f)

/** The smaller optical sizes [legibleOpticalSize] draws figures and punctuation at. */
private val LEGIBLE_OPTICAL_SIZES = listOf(6f, 7f, 8f, 9f, 10f, 11f, 12f, 14f, 16f, 18f, 20f)

/**
 * The optical sizes the app loads Bodoni Moda at. A size between two is drawn at the nearer one — a
 * family per pixel size would be a font load per pixel size.
 */
public val BODONI_OPTICAL_SIZES: List<Float> = LEGIBLE_OPTICAL_SIZES + CANVAS_OPTICAL_SIZES

public fun nearestOpticalSize(size: Float): Float = BODONI_OPTICAL_SIZES.minBy { kotlin.math.abs(it - size) }

/**
 * Bodoni Moda's hairline, in em, times its optical size: about 0.2 along the whole axis. Measured on the
 * bundled file's hyphen (B-106), whose height is the hairline: 72/2000 em at `opsz` 6, 38 at 11, 20 at 22,
 * 13 at 30, 8 at 44, 4 at 96, the same at every weight and in the italic. Drawn at its pixel size, as the
 * canvas's `font-optical-sizing: auto` draws it, the face's hairline is about 0.2 px at any size.
 */
private const val HAIRLINE_EM_TIMES_OPTICAL_SIZE = 0.2f

/** The thinnest hairline a figure or a punctuation mark is drawn with, in px. */
private const val MIN_HAIRLINE_PX = 1f

/**
 * The optical size a [size]-pixel Bodoni Moda draws figures and punctuation at: the largest loaded one
 * whose hairline is at least [MIN_HAIRLINE_PX] — `size / 5`, rounded down to a loaded size. Below 30 px
 * even the smallest (6) draws a thinner one, 0.8 px at 22, and that is what is drawn.
 */
public fun legibleOpticalSize(size: Float): Float {
    val largest = size * HAIRLINE_EM_TIMES_OPTICAL_SIZE / MIN_HAIRLINE_PX
    return BODONI_OPTICAL_SIZES.lastOrNull { it <= largest } ?: BODONI_OPTICAL_SIZES.first()
}
