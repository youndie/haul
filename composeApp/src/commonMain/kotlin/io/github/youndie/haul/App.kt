package io.github.youndie.haul

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.feature.identity.Identity
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.shell.Trees
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.theme.rememberHaulFonts
import io.github.youndie.haul.ui.LocalHaulNow
import io.github.youndie.haul.ui.LocalPhotoLoader
import io.github.youndie.haul.ui.PhotoLoader
import kotlinx.coroutines.delay
import kotlin.time.Clock

/**
 * The storefront's root: the theme, at the width the page has, and a «now» that ticks. The canvas draws
 * two widths, 1440 and 390; anything narrower than [COMPACT_BELOW] is drawn as the phone.
 *
 * [photos] draws the product photos the trees name (B-30); the entry point supplies it, because the
 * HTTP engine it loads with is the platform's. [trees] and [identity] are the stand-in host's (B-12).
 */
@Composable
public fun App(
    photos: PhotoLoader,
    trees: Trees,
    identity: Identity,
) {
    val now by produceState(wallClock()) {
        while (true) {
            delay(TICK_MILLIS)
            value = wallClock()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(HaulColors.background)) {
        CompositionLocalProvider(LocalPhotoLoader provides photos) {
            HaulTheme(rememberHaulFonts(), compact = maxWidth < COMPACT_BELOW) {
                CompositionLocalProvider(LocalHaulNow provides now) { Storefront(trees, identity) }
            }
        }
    }
}

@Suppress(
    "ktlint:kapkan:wall-clock",
    "The root is the one reader of the clock: the page's countdowns are this shopper's time, provided below as LocalHaulNow.",
)
private fun wallClock() = Clock.System.now()

private const val TICK_MILLIS = 1_000L
private val COMPACT_BELOW = 768.dp
