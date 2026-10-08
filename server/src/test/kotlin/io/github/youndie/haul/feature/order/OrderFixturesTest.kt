package io.github.youndie.haul.feature.order

import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.domain.Listed
import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.checkout.DeliveryMethod
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentPace
import io.github.youndie.haul.feature.fulfilment.domain.OrderProgress
import io.github.youndie.haul.feature.fulfilment.domain.ShipmentShares
import io.github.youndie.haul.feature.fulfilment.domain.TrackedOrder
import io.github.youndie.haul.feature.fulfilment.domain.TrackedShipment
import io.github.youndie.haul.feature.order.domain.CancelReason
import io.github.youndie.haul.feature.order.domain.NewOrder
import io.github.youndie.haul.feature.order.domain.Order
import io.github.youndie.haul.feature.order.domain.OrderLine
import io.github.youndie.haul.feature.order.domain.OrderStatus
import io.github.youndie.haul.feature.order.domain.Shipment
import io.github.youndie.haul.feature.order.domain.ShipmentStatus
import io.github.youndie.haul.feature.order.screen.Bought
import io.github.youndie.haul.feature.order.screen.OrderScreen
import io.github.youndie.haul.feature.reviews.screen.ReviewTabs
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.seed.SampleCheckout
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.testing.FulfilmentWorld
import io.github.youndie.haul.testing.SampleOrders
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.encodeKompotComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.toJavaDuration

/**
 * The client draws the Order screen's goldens from wire bodies (`composeApp/src/desktopTest/resources/
 * bodies/order_*.json`), and this holds each one equal to the tree the server builds for the artboard's
 * order — the same reason as the cart's and the checkout's (`CartFixturesTest`): a body written by hand
 * drifts from the wire without a sound. A mismatch writes what the server built to `build/order-fixtures/`.
 *
 * `Order_Placed` is Maya's cart placed by courier for Wed 8, 15:00–18:00 — #HL-48302, the first order a
 * fresh store gives — through placement itself, at the canvas's «now». The other orders are research §6's
 * history, which no seed holds: #HL-48211 in transit, #HL-47960 waiting at 214 Bedford Ave, #HL-46102
 * delivered (its «Return items» the return dialog, B-21), #HL-48303 declined, #HL-44019 returned. Each is written as the order it is ([SampleOrders]: its lines, its
 * shipments and where they are) and drawn by the same builder the route uses ([OrderScreen.page]), over the seeded
 * catalog, Maya's address and the points. Three of them bought products the seed does not sell (the yoga
 * mat, the sweater, the serum), so their tiles, options and sellers are given with the order.
 *
 * Every page is under the header the artboards draw: Maya, three in the cart.
 */
class OrderFixturesTest {
    private val bodies = File(System.getProperty("haul.clientBodies") ?: error("haul.clientBodies is not set"))
    private val pretty = Json(haulWireJson) { prettyPrint = true }
    private val canvasViewer = Viewer(firstName = "Maya", cartCount = 3, customerId = SampleCustomers.MAYA)

    @Test
    fun `the client's order bodies are the trees the server builds`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                runBlocking {
                    val screen = world.koin.get<OrderScreen>()
                    val mismatches = mutableListOf<String>()

                    fun check(
                        name: String,
                        tree: KompotComponent,
                    ) {
                        val built = Json.parseToJsonElement(haulWireJson.encodeKompotComponent(tree))
                        val drawn =
                            File(bodies, name).takeIf { it.exists() }?.let { Json.parseToJsonElement(it.readText()) }
                        if (drawn != built) {
                            File("build/order-fixtures").apply { mkdirs() }.resolve(name).writeText(
                                pretty.encodeToString(JsonElement.serializer(), built) + "\n",
                            )
                            mismatches += name
                        }
                    }

                    suspend fun page(
                        tracked: TrackedOrder,
                        products: Map<String, Bought> = emptyMap(),
                        sellers: Map<String, String> = emptyMap(),
                    ): KompotComponent {
                        val view = screen.view(SampleCustomers.MAYA, tracked, "Maya")
                        return screen.page(
                            view.copy(products = view.products + products, sellers = view.sellers + sellers),
                            canvasViewer,
                        )
                    }

                    // The canvas's own products, written as the catalog would hold them, so «Write a review» on a
                    // delivered line is the same dialog the route draws for a product it sells.
                    val sony = checkNotNull(world.koin.get<CatalogRepository>().product(SampleCatalog.SONY_HEADPHONES))
                    val tabs = world.koin.get<ReviewTabs>()

                    fun bought(
                        sku: String,
                        brand: String,
                        title: String,
                        options: Map<String, String>,
                        tone: String,
                    ): Map<String, Bought> {
                        val productId = sku.removeSuffix("-0")
                        val item =
                            Listed(
                                sony.product.copy(
                                    id = productId,
                                    brand = brand,
                                    title = title,
                                    tone = tone,
                                    dispatchDays = 0,
                                ),
                                listOf(sony.skus.first().copy(id = sku, productId = productId, options = options)),
                            )
                        val shown = options.entries.sortedBy { it.key != "colour" }.joinToString(" · ") { it.value }
                        return mapOf(
                            sku to
                                Bought(
                                    productId,
                                    shown,
                                    tone,
                                    0,
                                    tabs.writeReview(item, item.skus.single(), canvasViewer),
                                ),
                        )
                    }

                    val placedId = world.place(CheckoutChoice(slotId = WEDNESDAY_3PM))
                    check(PLACED, checkNotNull(screen.build(SampleCustomers.MAYA, placedId, canvasViewer)))
                    val mayas = checkNotNull(world.order(placedId)).placed

                    check(IN_TRANSIT, page(SampleOrders.inTransit(mayas)))
                    val yogaMat =
                        bought(
                            SampleOrders.YOGA_MAT_SKU,
                            "FlowFit",
                            "Natural Rubber Yoga Mat, 6 mm",
                            mapOf("colour" to "Black"),
                            "#E3F5D8",
                        )
                    check(
                        READY_FOR_PICKUP,
                        page(
                            SampleOrders.readyForPickup(),
                            yogaMat,
                            mapOf(SampleOrders.FLOWFIT to "FlowFit Studio"),
                        ),
                    )
                    val sweater =
                        bought(
                            SampleOrders.SWEATER_SKU,
                            "Northline",
                            "Merino Wool Crewneck Sweater, Unisex",
                            mapOf("colour" to "Moss", "size" to "M"),
                            "#FFE5DD",
                        )
                    val serum =
                        bought(
                            SampleOrders.SERUM_SKU,
                            "Clear Skin Lab",
                            "Vitamin C Brightening Serum, 30 ml",
                            mapOf("size" to "30 ml"),
                            "#F1E4F5",
                        )
                    check(
                        DELIVERED,
                        page(
                            SampleOrders.delivered(mayas),
                            sweater + serum,
                            mapOf(
                                SampleOrders.NORTHLINE to "Northline Knitwear",
                                SampleOrders.CLEAR_SKIN to "Clear Skin Lab",
                            ),
                        ),
                    )
                    check(CANCELLED, page(SampleOrders.cancelled(mayas)))

                    val returnedItems =
                        bought(
                            SampleOrders.BLOCKS_SKU,
                            "Brickworks",
                            "Building Blocks Space Station, 1,200 pcs",
                            mapOf("age" to "Ages 8+"),
                            "#FFF1C9",
                        ) +
                            bought(
                                SampleOrders.BAGS_SKU,
                                "Keepfresh",
                                "Silicone Food Storage Bags, Set of 6",
                                mapOf("colour" to "Clear"),
                                "#E3F5D8",
                            ) +
                            bought(
                                SampleOrders.CABLE_SKU,
                                "Voltline",
                                "USB-C Charging Cable, 2 m, 2-pack",
                                mapOf("colour" to "White"),
                                "#E6E4FF",
                            )
                    check(RETURNED, page(SampleOrders.returned(mayas), returnedItems))

                    assertEquals(
                        emptyList(),
                        mismatches,
                        "bodies that are not the server's trees (see build/order-fixtures)",
                    )
                }
            }
        }

    private companion object {
        const val PLACED = "order_placed.json"
        const val IN_TRANSIT = "order_in_transit.json"
        const val READY_FOR_PICKUP = "order_ready_for_pickup.json"
        const val DELIVERED = "order_delivered.json"
        const val CANCELLED = "order_cancelled.json"
        const val RETURNED = "order_returned.json"

        const val WEDNESDAY_3PM = "2025-10-08T15"
    }
}
