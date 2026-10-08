package io.github.youndie.haul

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.youndie.haul.feature.checkout.CheckoutBodyView
import io.github.youndie.haul.feature.checkout.CheckoutHeaderView
import io.github.youndie.haul.shell.CheckoutError
import io.github.youndie.haul.shell.CheckoutLoading
import io.github.youndie.haul.ui.CheckoutBody
import io.github.youndie.haul.ui.CheckoutHeader
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.viddik.annotations.ViddikScreenshot

// One fixture per artboard of the Checkout screen (B-15), at the artboard's size. The five server states
// are the server's own trees (`resources/bodies/checkout_*.json`, held equal to them by the server's
// `CheckoutFixturesTest`, which also says what in them is the canvas's: the points toggle, and
// PointsApplied as a whole) drawn through the app's registry. Placing is Content's tree with the order
// on its way, the state the renderer draws while placement answers; Loading and Error are the client's.

@ViddikScreenshot(name = "Loading", group = "Checkout", width = 1440, height = 1467)
@Composable
internal fun CheckoutLoadingWide() = Fixture(compact = false) { Page { CheckoutLoading() } }

@ViddikScreenshot(name = "Loading_Phone", group = "Checkout", width = 390, height = 2204)
@Composable
internal fun CheckoutLoadingPhone() = Fixture(compact = true) { Page { CheckoutLoading() } }

@ViddikScreenshot(name = "Content", group = "Checkout", width = 1440, height = 1564)
@Composable
internal fun CheckoutContentWide() = Fixture(compact = false) { Page { Body("checkout_content.json") } }

@ViddikScreenshot(name = "Content_Phone", group = "Checkout", width = 390, height = 2362)
@Composable
internal fun CheckoutContentPhone() = Fixture(compact = true) { Page { Body("checkout_content.json") } }

@ViddikScreenshot(name = "PointsApplied", group = "Checkout", width = 1440, height = 1564)
@Composable
internal fun CheckoutPointsAppliedWide() = Fixture(compact = false) { Page { Body("checkout_points_applied.json") } }

@ViddikScreenshot(name = "PointsApplied_Phone", group = "Checkout", width = 390, height = 2390)
@Composable
internal fun CheckoutPointsAppliedPhone() = Fixture(compact = true) { Page { Body("checkout_points_applied.json") } }

@ViddikScreenshot(name = "PickupPoint", group = "Checkout", width = 1440, height = 1267)
@Composable
internal fun CheckoutPickupPointWide() = Fixture(compact = false) { Page { Body("checkout_pickup_point.json") } }

@ViddikScreenshot(name = "PickupPoint_Phone", group = "Checkout", width = 390, height = 2005)
@Composable
internal fun CheckoutPickupPointPhone() = Fixture(compact = true) { Page { Body("checkout_pickup_point.json") } }

@ViddikScreenshot(name = "ParcelLocker", group = "Checkout", width = 1440, height = 1193)
@Composable
internal fun CheckoutParcelLockerWide() = Fixture(compact = false) { Page { Body("checkout_parcel_locker.json") } }

@ViddikScreenshot(name = "ParcelLocker_Phone", group = "Checkout", width = 390, height = 1802)
@Composable
internal fun CheckoutParcelLockerPhone() = Fixture(compact = true) { Page { Body("checkout_parcel_locker.json") } }

@ViddikScreenshot(name = "Validation", group = "Checkout", width = 1440, height = 1608)
@Composable
internal fun CheckoutValidationWide() = Fixture(compact = false) { Page { Body("checkout_validation.json") } }

@ViddikScreenshot(name = "Validation_Phone", group = "Checkout", width = 390, height = 2438)
@Composable
internal fun CheckoutValidationPhone() = Fixture(compact = true) { Page { Body("checkout_validation.json") } }

@ViddikScreenshot(name = "Placing", group = "Checkout", width = 1440, height = 1564)
@Composable
internal fun CheckoutPlacingWide() = Fixture(compact = false) { Page { Placing() } }

@ViddikScreenshot(name = "Placing_Phone", group = "Checkout", width = 390, height = 2362)
@Composable
internal fun CheckoutPlacingPhone() = Fixture(compact = true) { Page { Placing() } }

@ViddikScreenshot(name = "PlaceError", group = "Checkout", width = 1440, height = 1678)
@Composable
internal fun CheckoutPlaceErrorWide() = Fixture(compact = false) { Page { Body("checkout_place_error.json") } }

@ViddikScreenshot(name = "PlaceError_Phone", group = "Checkout", width = 390, height = 2509)
@Composable
internal fun CheckoutPlaceErrorPhone() = Fixture(compact = true) { Page { Body("checkout_place_error.json") } }

@ViddikScreenshot(name = "Error", group = "Checkout", width = 1440, height = 804)
@Composable
internal fun CheckoutErrorWide() = Fixture(compact = false) { Page { CheckoutError() } }

@ViddikScreenshot(name = "Error_Phone", group = "Checkout", width = 390, height = 529)
@Composable
internal fun CheckoutErrorPhone() = Fixture(compact = true) { Page { CheckoutError() } }

/** Content's tree as the renderer draws it while the order is on its way (Checkout_Placing). */
@Composable
private fun Placing() {
    val page = decode("checkout_content.json") as ColumnComponent
    Column(Modifier.fillMaxWidth()) {
        CheckoutHeaderView(page.children.filterIsInstance<CheckoutHeader>().single())
        CheckoutBodyView(page.children.filterIsInstance<CheckoutBody>().single(), placing = true)
    }
}
