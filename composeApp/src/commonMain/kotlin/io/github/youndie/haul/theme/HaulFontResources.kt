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

/**
 * The optical sizes the app loads Bodoni Moda at: the sizes the canvas draws it in. A size between
 * two is drawn at the nearer one — a family per pixel size would be a font load per pixel size.
 */
public val BODONI_OPTICAL_SIZES: List<Float> = listOf(22f, 24f, 26f, 28f, 30f, 34f, 40f, 44f, 48f, 56f, 72f, 84f, 96f)

public fun nearestOpticalSize(size: Float): Float = BODONI_OPTICAL_SIZES.minBy { kotlin.math.abs(it - size) }
