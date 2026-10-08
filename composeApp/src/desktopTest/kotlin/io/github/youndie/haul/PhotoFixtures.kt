package io.github.youndie.haul

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.ui.LocalPhotoLoader
import io.github.youndie.haul.ui.PHOTO_TAG
import io.github.youndie.haul.ui.PhotoLoader
import io.github.youndie.viddik.annotations.ViddikScreenshot

// A product card whose photo has loaded (B-30). Every other fixture draws the canvas's placeholder
// tiles — their bodies name no photo, and the fixtures' default loader loads nothing — so their goldens
// and their design parity are exactly what they were; this one shows what a stored photo looks like.
// The photo is painted here, not fetched or decoded: the golden is about the card, not about a codec.

@ViddikScreenshot(name = "Photo", group = "ProductCard", width = 236, height = 400)
@Composable
internal fun ProductCardPhoto() =
    Fixture(compact = false) {
        CompositionLocalProvider(LocalPhotoLoader provides StillLifeLoader) {
            Box(Modifier.width(236.dp)) { Body("product_card_photo.json") }
        }
    }

/** A loader whose every photo has already arrived: [StillLife], at once. */
internal val StillLifeLoader =
    PhotoLoader { _, modifier, _ ->
        Image(
            StillLife,
            contentDescription = null,
            modifier = modifier.testTag(PHOTO_TAG),
            contentScale = ContentScale.Crop,
        )
    }

/** A product photo painted in code: a lit backdrop, an object and its shadow, at any size. */
internal object StillLife : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        drawRect(
            Brush.linearGradient(
                listOf(Color(0xFFF3F1FF), Color(0xFFB9B5E0)),
                Offset.Zero,
                Offset(size.width, size.height),
            ),
        )
        val c = Offset(size.width / 2, size.height / 2)
        drawOval(
            Color(0xFF8E89B8),
            Offset(c.x - size.width * 0.3f, c.y + size.height * 0.24f),
            Size(
                size.width * 0.6f,
                size.height * 0.1f,
            ),
        )
        drawRoundRect(
            Brush.verticalGradient(
                listOf(Color(0xFF6E68A6), Color(0xFF3F3A70)),
                c.y - size.height * 0.27f,
                c.y + size.height * 0.25f,
            ),
            Offset(c.x - size.width * 0.23f, c.y - size.height * 0.27f),
            Size(size.width * 0.46f, size.height * 0.52f),
            androidx.compose.ui.geometry
                .CornerRadius(size.width * 0.09f),
        )
    }
}
