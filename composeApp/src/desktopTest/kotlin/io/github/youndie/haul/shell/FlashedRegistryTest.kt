package io.github.youndie.haul.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.Fixture
import io.github.youndie.haul.Page
import io.github.youndie.haul.decode
import io.github.youndie.haul.registry.haulRegistry
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotRegistry
import io.github.youndie.kompot.KompotScreen
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * B-163: under the storefront's root every node is drawn inside the box «Under the hood» flashes it in. The box passes
 * its constraints through, so with nothing flashing a page is drawn pixel for pixel as without it — the pages the
 * goldens draw, at 1440 and on a phone. A box that moved a node by a pixel would move the stand from its goldens.
 */
@OptIn(ExperimentalTestApi::class)
class FlashedRegistryTest {
    @Test
    fun `a page drawn in the flash's boxes is the page drawn without them`() {
        BODIES.forEach { body ->
            listOf(false, true).forEach { compact ->
                val width = if (compact) PHONE else WIDE
                val plain = draw(body, compact, width) { haulRegistry() }
                val boxed = draw(body, compact, width) { haulRegistry().flashedBy(UnderTheHood()) }
                assertContentEquals(plain, boxed, "$body at $width moved inside the flash's boxes")
            }
        }
    }

    private fun draw(
        body: String,
        compact: Boolean,
        width: Int,
        registry: () -> KompotRegistry,
    ): IntArray {
        var pixels = IntArray(0)
        runDesktopComposeUiTest(width, HEIGHT) {
            setContent { Fixture(compact) { Page { Drawn(body, registry) } } }
            waitForIdle()
            pixels = onRoot().captureToImage().toPixelMap().buffer
        }
        return pixels
    }

    @Composable
    private fun Drawn(
        body: String,
        registry: () -> KompotRegistry,
    ) {
        val drawn = remember { registry() }
        val forms = remember { FormController(FormSchema(formId = "none", fields = emptyList())) }
        KompotScreen(decode(body), drawn, forms, KompotActionHandler { })
    }

    private companion object {
        const val WIDE = 1440
        const val PHONE = 390
        const val HEIGHT = 2400
        val BODIES =
            listOf(
                "home_content.json",
                "catalog_content.json",
                "product_reviews.json",
                "search_results.json",
                "cart_content.json",
                "checkout_content.json",
                "order_in_transit.json",
                "account_content.json",
                "saved_content.json",
            )
    }
}
