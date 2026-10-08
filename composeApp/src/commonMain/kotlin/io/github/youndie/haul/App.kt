package io.github.youndie.haul

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.theme.rememberHaulFonts

/**
 * The storefront's root: the theme, at the width the page has. The canvas draws two widths, 1440 and
 * 390; anything narrower than [COMPACT_BELOW] is drawn as the phone.
 */
@Composable
public fun App() {
    BoxWithConstraints(Modifier.fillMaxSize().background(HaulColors.background)) {
        HaulTheme(rememberHaulFonts(), compact = maxWidth < COMPACT_BELOW) {}
    }
}

private val COMPACT_BELOW = 768.dp
