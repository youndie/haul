package io.github.youndie.haul

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.registry.haulJson
import io.github.youndie.haul.registry.haulRegistry
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.HeaderMenuSheet
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.LocalSearchField
import io.github.youndie.haul.ui.SearchFieldState
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotScreen
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.viddik.annotations.ViddikScreenshot
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.builtins.ListSerializer

// The two components every screen uses, drawn from bodies as the server sends them
// (`resources/bodies`), through the registry the app uses — so a golden here fails when the wire
// shape, the registry or the renderer moves, not only the last.
//
// Sizes are the artboards': the header is the top 186 px of every 1440-wide screen and the top 206 px
// of every 390-wide one; the cards are four cells of the catalog grid (236 px each, 24 px apart) and
// two of the phone grid (173 px, 12 px apart).

@ViddikScreenshot(name = "SignedIn", group = "HaulHeader", width = 1440, height = 186)
@Composable
internal fun HeaderSignedIn() = Fixture(compact = false) { Body("haul_header_signed_in.json") }

@ViddikScreenshot(name = "Guest", group = "HaulHeader", width = 1440, height = 186)
@Composable
internal fun HeaderGuest() = Fixture(compact = false) { Body("haul_header_guest.json") }

@ViddikScreenshot(name = "Search", group = "HaulHeader", width = 1440, height = 186)
@Composable
internal fun HeaderSearch() =
    Fixture(compact = false) {
        // The shopper is typing: the field is focused and draws its caret.
        CompositionLocalProvider(LocalSearchField provides remember { SearchFieldState(focused = true) }) {
            Body("haul_header_search.json")
        }
    }

@ViddikScreenshot(name = "SignedIn_Phone", group = "HaulHeader", width = 390, height = 206)
@Composable
internal fun HeaderSignedInPhone() = Fixture(compact = true) { Body("haul_header_signed_in.json") }

/**
 * B-73: the narrowest phone the store is drawn for, with a guest's «Sign in» — the widest account slot —
 * and the menu button: everything in the top row still fits, the category row runs past the edge.
 */
@ViddikScreenshot(name = "Guest_Phone375", group = "HaulHeader", width = 375, height = 206)
@Composable
internal fun HeaderGuestPhone375() = Fixture(compact = true) { Body("haul_header_guest.json") }

/**
 * B-73: the phone header's menu, open, for a customer: the account, «Orders», «Saved», «Deals», «HAUL PLUS»
 * and every top-level category of the seed. No artboard draws it, so it has no parity reference.
 */
@ViddikScreenshot(name = "Menu_Phone", group = "HaulHeader", width = 390, height = 844)
@Composable
internal fun HeaderMenuPhone() = Fixture(compact = true) { HeaderMenuSheet(menuHeader("Maya")) }

/** B-73: the same for a guest, whose account entry is «Sign in». */
@ViddikScreenshot(name = "Menu_Guest_Phone", group = "HaulHeader", width = 390, height = 844)
@Composable
internal fun HeaderMenuGuestPhone() = Fixture(compact = true) { HeaderMenuSheet(menuHeader(null)) }

/** The header as the server sends it, with the catalog: the seed's 32 top-level categories (`CatalogSeed`). */
private fun menuHeader(customerName: String?): HaulHeader =
    (decode("haul_header_signed_in.json") as HaulHeader).copy(
        customerName = customerName,
        catalog = TOP_LEVEL.map { Link(it) },
    )

private val TOP_LEVEL =
    listOf(
        "Electronics",
        "Home & Kitchen",
        "Fashion",
        "Beauty",
        "Kids & Toys",
        "Sports",
        "Grocery",
        "Auto",
        "Books",
        "Pets",
        "Garden",
        "Office",
        "Health",
        "Music",
        "Movies",
        "Games",
        "Tools",
        "Baby",
        "Jewelry",
        "Shoes",
        "Luggage",
        "Crafts",
        "Outdoors",
        "Party",
        "Furniture",
        "Lighting",
        "Appliances",
        "Computers",
        "Phones",
        "Cameras",
        "Watches",
        "Bags",
    )

@ViddikScreenshot(name = "Grid", group = "ProductCard", width = 1016, height = 400)
@Composable
internal fun ProductCardGrid() = Fixture(compact = false) { Cards(cell = 236, gap = 24) }

@ViddikScreenshot(name = "Grid_Phone", group = "ProductCard", width = 358, height = 330)
@Composable
internal fun ProductCardGridPhone() = Fixture(compact = true) { Cards(cell = 173, gap = 12, take = 2) }

@Composable
private fun Cards(
    cell: Int,
    gap: Int,
    take: Int = Int.MAX_VALUE,
) {
    val cards =
        remember {
            haulJson
                .decodeFromString(
                    ListSerializer(PolymorphicSerializer(KompotComponent::class)),
                    read("product_cards.json"),
                ).take(take)
        }
    Row(horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
        cards.forEach { Box(Modifier.width(cell.dp)) { Render(it) } }
    }
}
