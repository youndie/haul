package io.github.youndie.haul

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.ui.Text
import io.github.youndie.haul.ui.accented
import io.github.youndie.viddik.annotations.ViddikScreenshot

// B-106: the characters the display face lost — the hyphen, the en and em dash, the minus, «#», the comma, and the 4
// that read as a 1 — at the sizes the canvas sets Bodoni Moda in up to 56 px, then the big figures and an accented
// title. Its letters are the canvas's cut, hairlines and all: an H still loses its bar (B-106's findings).
// No artboard draws this, so it has no parity reference.

/** The sizes the canvas sets Bodoni Moda in, from the 20 px search price to the 56 px phone title. */
private val TEXT_SIZES = listOf(20f, 22f, 24f, 26f, 28f, 30f, 32f, 34f, 36f, 40f, 44f, 48f, 56f)

/** The sizes of the big figures: a pickup code, a rating, the desktop titles. */
private val FIGURE_SIZES = listOf(72f, 96f, 112f)

@ViddikScreenshot(name = "Characters", group = "DisplayFace", width = 1440, height = 900)
@Composable
internal fun DisplayFaceCharacters() =
    Fixture(compact = false) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TEXT_SIZES.forEach { size ->
                Text("SH-718N — #HL-48303 – $31.49 · 4.8 · −$14", HaulType.display(size, 800), softWrap = false)
            }
            FIGURE_SIZES.forEach { size ->
                Text("$31.49 — 4.8 — 4821", HaulType.display(size, 800), softWrap = false)
            }
            Text(
                accented("Thanks, Sam — order #HL-48303 is placed", "#HL-48303"),
                style = HaulType.display(56f, 800, letterSpacing = -0.01f),
                softWrap = false,
            )
        }
    }
