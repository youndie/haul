package io.github.youndie.haul

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.youndie.haul.registry.haulJson
import io.github.youndie.haul.registry.haulRegistry
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.LocalHaulNow
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotScreen
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import kotlinx.serialization.PolymorphicSerializer
import kotlin.time.Instant

// What every screenshot fixture is drawn in: the theme with the fixture fonts, the canvas's Paper, and
// the canvas's «now» (2025-10-07 19:47:23 in New York), so a countdown reads what the artboard reads.

/** The canvas's «now» (`canvas.json`, `now`). */
internal val CANVAS_NOW: Instant = Instant.parse("2025-10-07T19:47:23-04:00")

@Composable
internal fun Fixture(
    compact: Boolean,
    content: @Composable () -> Unit,
) {
    HaulTheme(FixtureFonts.fonts, compact) {
        CompositionLocalProvider(LocalHaulNow provides CANVAS_NOW) {
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
