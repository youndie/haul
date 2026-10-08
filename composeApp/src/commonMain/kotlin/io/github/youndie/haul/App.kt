package io.github.youndie.haul

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.theme.rememberHaulFonts
import io.github.youndie.haul.ui.LocalPhotoLoader
import io.github.youndie.haul.ui.PhotoLoader

/**
 * The storefront's root: the theme, at the width the page has. The canvas draws two widths, 1440 and
 * 390; anything narrower than [COMPACT_BELOW] is drawn as the phone.
 *
 * [photos] draws the product photos the trees name (B-30); the entry point supplies it, because the
 * HTTP engine it loads with is the platform's.
 */
@Composable
public fun App(photos: PhotoLoader) {
    BoxWithConstraints(Modifier.fillMaxSize().background(HaulColors.background)) {
        CompositionLocalProvider(LocalPhotoLoader provides photos) {
            HaulTheme(rememberHaulFonts(), compact = maxWidth < COMPACT_BELOW) {}
        }
    }
}

private val COMPACT_BELOW = 768.dp
