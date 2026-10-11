package io.github.youndie.haul.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.CANVAS_NOW
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.HaulThemeAtWidth
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.registry.haulJson
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.ui.LocalHaulNow
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductCardView
import io.github.youndie.kompot.KompotComponent
import kotlinx.serialization.PolymorphicSerializer
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * B-120: a card whose SKU has nothing in stock comes without a delivery day, and draws none — the card ends at
 * its rating, with no line left for a day, beside a card that can be bought and keeps its «TOMORROW».
 */
@OptIn(ExperimentalTestApi::class)
class OutOfStockCardTest {
    private val inStock =
        ProductCard(
            id = "card-in",
            productId = "p-in",
            title = "A product in stock",
            price = "$24",
            rating = "4.6",
            reviews = "120",
            delivery = "Tomorrow",
            tone = "#E3F5D8",
            label = "mug",
        )

    @Test
    fun `a card out of stock comes without a day and draws none`() =
        runDesktopComposeUiTest(600, 500) {
            val wire =
                haulWireJson.encodeToString(
                    PolymorphicSerializer(KompotComponent::class),
                    inStock.copy(id = "card-gone", productId = "p-gone", reviews = "7", delivery = null),
                )
            assertFalse("\"delivery\"" in wire, "the server's encoding leaves the absent day out: $wire")
            val gone = haulJson.decodeFromString(PolymorphicSerializer(KompotComponent::class), wire) as ProductCard

            setContent {
                HaulThemeAtWidth(FixtureFonts.fonts, Modifier.fillMaxSize().background(HaulColors.background)) {
                    CompositionLocalProvider(LocalHaulNow provides CANVAS_NOW) {
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.width(236.dp).testTag(IN)) { ProductCardView(inStock) }
                            Box(Modifier.width(236.dp).testTag(GONE)) { ProductCardView(gone) }
                        }
                    }
                }
            }

            assertEquals(1, onAllNodesWithText("TOMORROW").fetchSemanticsNodes().size, "one card promises a day")
            val goneCard = onNodeWithTag(GONE).fetchSemanticsNode().boundsInRoot
            val rating = onNodeWithText("· 7", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(
                abs(goneCard.bottom - rating.bottom) < 1f,
                "the card goes on past its rating: $goneCard, $rating",
            )
            assertTrue(goneCard.bottom < onNodeWithTag(IN).fetchSemanticsNode().boundsInRoot.bottom)
        }

    private companion object {
        const val IN = "in-stock"
        const val GONE = "out-of-stock"
    }
}
