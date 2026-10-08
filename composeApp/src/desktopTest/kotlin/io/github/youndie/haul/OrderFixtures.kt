package io.github.youndie.haul

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.youndie.haul.feature.order.OrderNotFound
import io.github.youndie.haul.feature.order.ReturnFormView
import io.github.youndie.haul.feature.product.DialogOverlay
import io.github.youndie.haul.feature.returns.ReturnEntry
import io.github.youndie.haul.shell.ErrorShell
import io.github.youndie.haul.shell.OrderLoading
import io.github.youndie.haul.shell.ShellFailure
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.OrderBody
import io.github.youndie.haul.ui.ReturnForm
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.PresentAction
import io.github.youndie.viddik.annotations.ViddikScreenshot

// One fixture per artboard of the Order screen (B-18), at the artboard's size. The five server states are
// the server's own trees (`resources/bodies/order_*.json`, held equal to them by the server's
// `OrderFixturesTest`, which also says which orders they are) drawn through the app's registry. Loading,
// NotFound and Error are the client's: an order that is not there answers `404` with no tree, and is drawn
// under the header last drawn — Maya's, with three in the cart, as on the canvas. ReturnDialog is Delivered
// with the form its «Return items» presents drawn over it, holding what the canvas ticked and chose; Returned
// is the server's tree of a refunded return (B-21).

@ViddikScreenshot(name = "Loading", group = "Order", width = 1440, height = 1205)
@Composable
internal fun OrderLoadingWide() = Fixture(compact = false) { Page { OrderLoading() } }

@ViddikScreenshot(name = "Loading_Phone", group = "Order", width = 390, height = 1348)
@Composable
internal fun OrderLoadingPhone() = Fixture(compact = true) { Page { OrderLoading() } }

@ViddikScreenshot(name = "Placed", group = "Order", width = 1440, height = 1446)
@Composable
internal fun OrderPlacedWide() = Fixture(compact = false) { Page { Body("order_placed.json") } }

@ViddikScreenshot(name = "Placed_Phone", group = "Order", width = 390, height = 1814)
@Composable
internal fun OrderPlacedPhone() = Fixture(compact = true) { Page { Body("order_placed.json") } }

@ViddikScreenshot(name = "InTransit", group = "Order", width = 1440, height = 1345)
@Composable
internal fun OrderInTransitWide() = Fixture(compact = false) { Page { Body("order_in_transit.json") } }

@ViddikScreenshot(name = "InTransit_Phone", group = "Order", width = 390, height = 1662)
@Composable
internal fun OrderInTransitPhone() = Fixture(compact = true) { Page { Body("order_in_transit.json") } }

@ViddikScreenshot(name = "ReadyForPickup", group = "Order", width = 1440, height = 1074)
@Composable
internal fun OrderReadyForPickupWide() = Fixture(compact = false) { Page { Body("order_ready_for_pickup.json") } }

@ViddikScreenshot(name = "ReadyForPickup_Phone", group = "Order", width = 390, height = 1481)
@Composable
internal fun OrderReadyForPickupPhone() = Fixture(compact = true) { Page { Body("order_ready_for_pickup.json") } }

@ViddikScreenshot(name = "Delivered", group = "Order", width = 1440, height = 1125)
@Composable
internal fun OrderDeliveredWide() = Fixture(compact = false) { Page { Body("order_delivered.json") } }

@ViddikScreenshot(name = "Delivered_Phone", group = "Order", width = 390, height = 1581)
@Composable
internal fun OrderDeliveredPhone() = Fixture(compact = true) { Page { Body("order_delivered.json") } }

@ViddikScreenshot(name = "ReturnDialog", group = "Order", width = 1440, height = 1125)
@Composable
internal fun OrderReturnDialogWide() = Fixture(compact = false) { ReturnDialogOverDelivered() }

@ViddikScreenshot(name = "ReturnDialog_Phone", group = "Order", width = 390, height = 1581)
@Composable
internal fun OrderReturnDialogPhone() = Fixture(compact = true) { ReturnDialogOverDelivered() }

@ViddikScreenshot(name = "Returned", group = "Order", width = 1440, height = 1146)
@Composable
internal fun OrderReturnedWide() = Fixture(compact = false) { Page { Body("order_returned.json") } }

@ViddikScreenshot(name = "Returned_Phone", group = "Order", width = 390, height = 1446)
@Composable
internal fun OrderReturnedPhone() = Fixture(compact = true) { Page { Body("order_returned.json") } }

@ViddikScreenshot(name = "Cancelled", group = "Order", width = 1440, height = 1193)
@Composable
internal fun OrderCancelledWide() = Fixture(compact = false) { Page { Body("order_cancelled.json") } }

@ViddikScreenshot(name = "Cancelled_Phone", group = "Order", width = 390, height = 1550)
@Composable
internal fun OrderCancelledPhone() = Fixture(compact = true) { Page { Body("order_cancelled.json") } }

@ViddikScreenshot(name = "NotFound", group = "Order", width = 1440, height = 719)
@Composable
internal fun OrderNotFoundWide() = Fixture(compact = false) { Page { OrderNotFound(mayasHeader()) } }

@ViddikScreenshot(name = "NotFound_Phone", group = "Order", width = 390, height = 545)
@Composable
internal fun OrderNotFoundPhone() = Fixture(compact = true) { Page { OrderNotFound(mayasHeader()) } }

@ViddikScreenshot(name = "Error", group = "Order", width = 1440, height = 900)
@Composable
internal fun OrderErrorWide() = Fixture(compact = false) { Page { ErrorShell("This order", ShellFailure.Server) } }

@ViddikScreenshot(name = "Error_Phone", group = "Order", width = 390, height = 641)
@Composable
internal fun OrderErrorPhone() = Fixture(compact = true) { Page { ErrorShell("This order", ShellFailure.Server) } }

/** Order_ReturnDialog: the sweater ticked and «Doesn’t fit» chosen, over #HL-46102 delivered. */
@Composable
private fun ReturnDialogOverDelivered() {
    val form =
        remember {
            val body =
                (
                    decode(
                        "order_delivered.json",
                    ) as ColumnComponent
                ).children.filterIsInstance<OrderBody>().single()
            (body.summary.returnAction as PresentAction).content as ReturnForm
        }
    Page { Body("order_delivered.json") }
    DialogOverlay(onDismiss = null) {
        ReturnFormView(form, ReturnEntry(lines = listOf(0), reason = "doesnt_fit"))
    }
}

/** The header the last page drew: Placed's, Maya's with three in the cart. */
@Composable
private fun mayasHeader(): HaulHeader =
    remember { (decode("order_placed.json") as ColumnComponent).children.filterIsInstance<HaulHeader>().single() }
