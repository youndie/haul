package io.github.youndie.haul

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * The storefront's root. An empty Paper page until the theme and the first components arrive (B-04);
 * the colour is the canvas's background role.
 */
@Composable
public fun App() {
    Box(Modifier.fillMaxSize().background(PAPER))
}

private val PAPER = Color(0xFFF5F3EE)
