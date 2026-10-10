package io.github.youndie.haul

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.cart.LineCommand
import io.github.youndie.haul.feature.catalog.FacetPanelView
import io.github.youndie.haul.registry.haulJson
import io.github.youndie.haul.shell.MessageNotice
import io.github.youndie.haul.ui.Facet
import io.github.youndie.haul.ui.FacetOption
import io.github.youndie.haul.ui.FacetPanel
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductDetails
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.ShowMessageAction
import io.github.youndie.viddik.annotations.ViddikScreenshot
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.builtins.ListSerializer

// B-75: what a press answers with, and what has nothing to press. No artboard draws any of it — the canvas
// has no message, no full line in the cart, no facet left empty by another — so each is drawn from the
// theme's tokens and has no parity reference.

/** «Add to cart»'s message over the page: the words and the way to the cart. */
@ViddikScreenshot(name = "Message", group = "Shell", width = 1440, height = 100)
@Composable
internal fun ShellMessage() = Fixture(compact = false) { Message() }

@ViddikScreenshot(name = "Message_Phone", group = "Shell", width = 390, height = 100)
@Composable
internal fun ShellMessagePhone() = Fixture(compact = true) { Message() }

@Composable
private fun Message() {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        MessageNotice(ShowMessageAction("Added to your cart", actionLabel = "View cart"), onAction = {})
    }
}

/** A card with something to add beside one with nothing: out of stock, or as many in the cart as can be bought. */
@ViddikScreenshot(name = "NothingToAdd", group = "ProductCard", width = 496, height = 400)
@Composable
internal fun ProductCardNothingToAdd() =
    Fixture(compact = false) {
        val cards =
            remember {
                haulJson
                    .decodeFromString(
                        ListSerializer(PolymorphicSerializer(KompotComponent::class)),
                        read("product_cards.json"),
                    ).take(2)
                    .mapIndexed { index, card -> if (index == 1) (card as ProductCard).copy(add = null) else card }
            }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            cards.forEach { Box(Modifier.width(236.dp)) { Render(it) } }
        }
    }

/** The product page once «Add to cart» went through: one in the cart, and one more to add. */
@ViddikScreenshot(name = "InCart", group = "Product", width = 1440, height = 900)
@Composable
internal fun ProductInCart() = Fixture(compact = false) { Page { Render(inCart(1)) } }

/** At the line's limit: ten in the cart, «Add to cart» with nothing to add, «Buy now» still there. */
@ViddikScreenshot(name = "InCart_AtLimit", group = "Product", width = 1440, height = 900)
@Composable
internal fun ProductInCartAtLimit() = Fixture(compact = false) { Page { Render(inCart(10)) } }

/** The description tab's page as the server draws it with [count] of the SKU shown in the cart. */
private fun inCart(count: Int): KompotComponent {
    val page = decode("product_description.json") as ColumnComponent
    return page.copy(
        children =
            page.children.map { child ->
                if (child !is ProductDetails) {
                    child
                } else {
                    child.copy(
                        add =
                            child.add?.takeIf { count < MAX_LINE }?.let {
                                LineCommand(
                                    it.url,
                                    LineChange(
                                        quantity =
                                            count + 1,
                                    ),
                                )
                            },
                        inCart = Link("$count in your cart", NavigateAction("/cart")),
                    )
                }
            },
    )
}

private const val MAX_LINE = 10

/**
 * The facet column after another filter left some options with nothing to show: a brand, the delivery toggle,
 * a colour and a feature at 0 are faded and press nothing; a brand at 0 that is ticked keeps its look, since
 * pressing it is how it is unticked.
 */
@ViddikScreenshot(name = "Facets_ZeroCounts", group = "Catalog", width = 280, height = 560)
@Composable
internal fun CatalogFacetsZeroCounts() =
    Fixture(compact = false) {
        FacetPanelView(ZERO_COUNTS, Modifier.padding(16.dp), sheet = false)
    }

private val PRESS = NavigateAction("/c/mugs?brand=Ostra")

private val ZERO_COUNTS =
    FacetPanel(
        "facets",
        listOf(
            Facet(
                "brand",
                "Brand",
                "checkbox",
                options =
                    listOf(
                        FacetOption("Ostra", 12, selected = false, action = PRESS),
                        FacetOption("Lume", 0, selected = true, action = PRESS),
                        FacetOption("Hearth", 0, selected = false),
                    ),
            ),
            Facet("delivery", "Delivery", "toggle", options = listOf(FacetOption("Tomorrow", 0, selected = false))),
            Facet(
                "colour",
                "Color",
                "swatch",
                options =
                    listOf(
                        FacetOption("Black", 4, selected = false, swatch = "#1B1B1F", action = PRESS),
                        FacetOption("Green", 0, selected = false, swatch = "#7BB58A"),
                    ),
            ),
            Facet(
                "feature",
                "Features",
                "pills",
                options =
                    listOf(
                        FacetOption("Dishwasher safe", 9, selected = false, action = PRESS),
                        FacetOption("Lid", 0, selected = false),
                    ),
            ),
        ),
    )
