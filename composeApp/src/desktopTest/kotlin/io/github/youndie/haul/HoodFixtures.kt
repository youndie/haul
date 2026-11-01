package io.github.youndie.haul

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.youndie.haul.shell.HoodRequest
import io.github.youndie.haul.shell.HoodUpdate
import io.github.youndie.haul.shell.UnderTheHood
import io.github.youndie.haul.shell.UnderTheHoodPanel
import io.github.youndie.haul.ui.DemoNote
import io.github.youndie.haul.ui.DemoNoteView
import io.github.youndie.haul.ui.Motion
import io.github.youndie.viddik.annotations.ViddikScreenshot
import kotlin.time.Duration.Companion.milliseconds

// «Under the hood» (B-163): the demo strip with its toggle, closed and open, and the panel over the guest's home —
// its request, a card's «+» it took while open, and its tree with the header opened. No artboard draws either; the
// numbers are fixed here, as a request on the stand would have them.

@ViddikScreenshot(name = "Strip_Hood", group = "Demo", width = 1440, height = 224)
@Composable
internal fun DemoStripHoodWide() = Fixture(compact = false) { StripWithToggle(open = false) }

@ViddikScreenshot(name = "Strip_Hood_Phone", group = "Demo", width = 390, height = 300)
@Composable
internal fun DemoStripHoodPhone() = Fixture(compact = true) { StripWithToggle(open = false) }

@ViddikScreenshot(name = "Panel", group = "Hood", width = 1440, height = 900)
@Composable
internal fun HoodPanelWide() = Fixture(compact = false) { PanelOverHome() }

@ViddikScreenshot(name = "Panel_Phone", group = "Hood", width = 390, height = 844)
@Composable
internal fun HoodPanelPhone() = Fixture(compact = true) { PanelOverHome() }

/** The strip with the toggle, over the header of the page it sits above. */
@Composable
private fun StripWithToggle(open: Boolean) {
    Column {
        DemoNoteView(decode("demo_note.json") as DemoNote, onClose = {}, onHood = {}, hoodOpen = open)
        Body("haul_header_guest.json")
    }
}

/** The guest's home under the strip, its toggle on, and the panel over it. */
@Composable
private fun PanelOverHome() {
    val hood = remember { openHood() }
    Box(Modifier.fillMaxSize()) {
        Page {
            Column {
                DemoNoteView(decode("demo_note.json") as DemoNote, onClose = {}, onHood = {}, hoodOpen = true)
                Body("home_guest.json")
            }
        }
        UnderTheHoodPanel(hood, "Under the hood")
    }
}

private fun openHood(): UnderTheHood =
    UnderTheHood(Motion.Still).apply {
        toggle()
        heard(HoodRequest(HOME, 200, read("home_guest.json").length, 12.4, 38.milliseconds))
        shown(decode("home_guest.json"), HOME)
        updates.add(HoodUpdate("PUT", LINE, listOf("header", "card-1"), null, 41.milliseconds))
        screen?.expanded?.set("0/0", true)
    }

private const val HOME = "/ui/home"
private const val LINE = "/api/v1/cart/lines/p-1-0?answer=card"
