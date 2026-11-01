package io.github.youndie.haul

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import io.github.youndie.haul.registry.haulJson
import io.github.youndie.haul.registry.haulRegistry
import io.github.youndie.haul.theme.HaulColorScheme
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.LocalHaulNow
import io.github.youndie.haul.ui.LocalPhotoLoader
import io.github.youndie.haul.ui.PHOTO_TAG
import io.github.youndie.haul.ui.PhotoLoader
import io.github.youndie.haul.ui.VectorPicturePainter
import io.github.youndie.haul.ui.vectorPicture
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotScreen
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import kotlinx.serialization.PolymorphicSerializer
import kotlin.time.Instant

// What every screenshot fixture is drawn in: the theme with the fixture fonts, the canvas's Paper, the
// canvas's «now» (2025-10-07 19:47:23 in New York), so a countdown reads what the artboard reads, and the
// seed's pictures the bodies name (B-78), already arrived, so a golden shows what a shopper gets.

/** The canvas's «now» (`canvas.json`, `now`). */
internal val CANVAS_NOW: Instant = Instant.parse("2025-10-07T19:47:23-04:00")

/**
 * The scheme a fixture is drawn in: the canvas's light one, unless a dark golden asks for the dark one around the very
 * fixture its light golden draws ([Dark], B-180).
 */
internal val LocalFixtureScheme = staticCompositionLocalOf { HaulColorScheme.Light }

/** [fixture] — one of the light goldens' fixtures — drawn in the dark scheme (B-180, `DarkFixtures.kt`). */
@Composable
internal fun Dark(fixture: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFixtureScheme provides HaulColorScheme.Dark, content = fixture)
}

@Composable
internal fun Fixture(
    compact: Boolean,
    medium: Boolean = false,
    content: @Composable () -> Unit,
) {
    HaulTheme(FixtureFonts.fonts, compact, medium, LocalFixtureScheme.current) {
        CompositionLocalProvider(LocalHaulNow provides CANVAS_NOW, LocalPhotoLoader provides ArtLoader) {
            Box(
                Modifier.fillMaxSize().background(HaulColors.background),
                contentAlignment = Alignment.TopStart,
            ) { content() }
        }
    }
}

/** A whole page, as tall as it is: the artboard cuts it where the canvas does. */
@Composable
internal fun Page(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { content() }
}

@Composable
internal fun Body(name: String) {
    Render(decode(name))
}

internal fun decode(name: String): KompotComponent =
    haulJson.decodeFromString(PolymorphicSerializer(KompotComponent::class), read(name))

@Composable
internal fun Render(component: KompotComponent) {
    val registry = remember { haulRegistry() }
    val forms = remember { FormController(FormSchema(formId = "none", fields = emptyList())) }
    KompotScreen(component, registry, forms, KompotActionHandler { })
}

internal fun read(name: String): String =
    checkNotNull(object {}.javaClass.classLoader.getResourceAsStream("bodies/$name")) {
        "no body $name"
    }.use { it.readBytes().decodeToString() }

/**
 * The seed's pictures (B-78), drawn at once from `resources/art` — the server's own SVGs and their dark versions
 * (B-181), which ArtFixturesTest holds there — as the app draws them: recorded once and replayed as vectors
 * ([vectorPicture]). Any other image (a stored photo) is not loaded, and its placeholder tile is drawn.
 */
internal val ArtLoader =
    PhotoLoader { url, modifier, placeholder ->
        val name = url.removePrefix(ART_PREFIX).takeIf { url.startsWith(ART_PREFIX) }
        val painter =
            remember(name) {
                name?.let { checkNotNull(vectorPicture(art(it))) { "$it is no picture" } }?.let(::VectorPicturePainter)
            }
        if (painter == null) {
            Box(modifier) { placeholder() }
        } else {
            Image(
                painter,
                contentDescription = null,
                modifier = modifier.testTag(PHOTO_TAG),
                contentScale = ContentScale.Crop,
            )
        }
    }

private const val ART_PREFIX = "/images/art/"

private fun art(name: String): ByteArray =
    checkNotNull(object {}.javaClass.classLoader.getResourceAsStream("art/$name")) {
        "no picture $name: ArtFixturesTest writes the missing ones to server/build/art-fixtures"
    }.use { it.readBytes() }
