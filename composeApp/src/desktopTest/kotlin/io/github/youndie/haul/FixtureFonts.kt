package io.github.youndie.haul

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import io.github.youndie.haul.theme.HaulFonts
import io.github.youndie.viddik.core.ViddikPlatformTextStyle
import io.github.youndie.viddik.core.normalizeVerticalMetrics
import java.io.File

/**
 * The app's three font files, read the way a golden needs them: vertical metrics normalised and the
 * rasterisation pinned, so a golden recorded on one operating system holds on another. The files
 * are the ones the app ships (`composeResources/font`), not copies.
 */
internal object FixtureFonts {
    private fun bytes(name: String): ByteArray =
        normalizeVerticalMetrics(File("src/commonMain/composeResources/font/$name").readBytes())

    private val archivo by lazy { bytes("archivo.ttf") }
    private val mono by lazy { bytes("jetbrains_mono.ttf") }
    private val bodoni by lazy { bytes("bodoni_moda.ttf") }

    private fun family(
        name: String,
        data: ByteArray,
        weights: List<Int>,
        vararg extra: FontVariation.Setting,
    ): FontFamily =
        FontFamily(
            weights.map {
                Font(
                    identity = "$name-$it-${extra.joinToString { s -> s.toString() }}",
                    data = data,
                    weight = FontWeight(it),
                    variationSettings = FontVariation.Settings(FontVariation.weight(it), *extra),
                )
            },
        )

    val fonts: HaulFonts by lazy {
        val bodoniCache = mutableMapOf<Float, FontFamily>()
        HaulFonts(
            archivo = family("archivo", archivo, listOf(400, 500, 600, 700, 800)),
            mono = family("mono", mono, listOf(500, 600)),
            bodoniAt = { opsz ->
                bodoniCache.getOrPut(
                    opsz,
                ) { family("bodoni", bodoni, listOf(800, 900), FontVariation.Setting("opsz", opsz)) }
            },
            platformStyle = ViddikPlatformTextStyle,
        )
    }
}
