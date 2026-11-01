package io.github.youndie.haul

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import io.github.youndie.haul.ui.DemoNote
import io.github.youndie.haul.ui.DemoNoteView
import io.github.youndie.viddik.annotations.ViddikScreenshot

// What the stand says about itself (B-160): the demo strip above the guest's header, as the storefront draws it over
// every page until it is closed, and the About page. No artboard draws either; both are drawn from the server's
// bodies (`resources/bodies/demo_note.json`, `about_content.json`, held to the server by `AboutRoutesTest`).

@ViddikScreenshot(name = "Strip", group = "Demo", width = 1440, height = 224)
@Composable
internal fun DemoStripWide() = Fixture(compact = false) { StripOverHeader() }

@ViddikScreenshot(name = "Strip_Phone", group = "Demo", width = 390, height = 277)
@Composable
internal fun DemoStripPhone() = Fixture(compact = true) { StripOverHeader() }

@ViddikScreenshot(name = "Content", group = "About", width = 1440, height = 2386)
@Composable
internal fun AboutContentWide() = Fixture(compact = false) { Page { Body("about_content.json") } }

@ViddikScreenshot(name = "Content_Phone", group = "About", width = 390, height = 2914)
@Composable
internal fun AboutContentPhone() = Fixture(compact = true) { Page { Body("about_content.json") } }

/** The strip with its close control, over the header of the page it sits above. */
@Composable
private fun StripOverHeader() {
    Column {
        DemoNoteView(decode("demo_note.json") as DemoNote, onClose = {})
        Body("haul_header_guest.json")
    }
}
