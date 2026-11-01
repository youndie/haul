package io.github.youndie.haul

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import io.github.youndie.haul.feature.live.LiveOrdersBodyView
import io.github.youndie.haul.ui.LiveOrdersBody
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.viddik.annotations.ViddikScreenshot

// «Live orders» (B-164): five orders in the states the stand's walks leave them in — just placed, declined by the
// test card and undone, on its way to a pickup point, delivered and paid on delivery, returned and refunded — the
// declined one opened to its trail. No artboard draws the page; it is drawn from the server's body
// (`resources/bodies/live_orders.json`, held to the server by `LiveFixturesTest`) in the frame of the guest's header
// and footer.

@ViddikScreenshot(name = "Content", group = "Live", width = 1440, height = 2464)
@Composable
internal fun LiveContentWide() = Fixture(compact = false) { Page { LivePage() } }

@ViddikScreenshot(name = "Content_Phone", group = "Live", width = 390, height = 3940)
@Composable
internal fun LiveContentPhone() = Fixture(compact = true) { Page { LivePage() } }

/** The page as the storefront draws it, the declined order open: the header, the body, the footer. */
@Composable
private fun LivePage() {
    val page = decode("live_orders.json") as ColumnComponent
    Column {
        page.children.forEach { child ->
            if (child is LiveOrdersBody) LiveOrdersBodyView(child, open = setOf(DECLINED)) else Render(child)
        }
    }
}

private const val DECLINED = "HL-48310"
