package io.github.youndie.haul.feature.account

import io.github.youndie.haul.feature.account.screen.AccountPage
import io.github.youndie.haul.feature.account.screen.AccountScreen
import io.github.youndie.haul.feature.account.screen.AccountView
import io.github.youndie.haul.feature.account.screen.OrderState
import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.fulfilment.domain.TrackedOrder
import io.github.youndie.haul.feature.identity.domain.Customer
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.CatalogSeed
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The client draws the Account screen's goldens from wire bodies (`composeApp/src/desktopTest/resources/
 * bodies/account_*.json`), and this holds each one equal to the tree the server builds for the artboard's
 * customer — the same reason as the cart's and the order's (`CartFixturesTest`, `OrderFixturesTest`): a
 * body written by hand drifts from the wire without a sound. A mismatch writes what the server built to
 * `build/account-fixtures/`.
 *
 * The orders are research §6's history, which no seed holds ([SampleOrders]): written as tracking reads
 * them and drawn by the builder the route uses ([AccountScreen.view], [AccountScreen.page]), over the seeded
 * catalog and Maya's checkout; the points and the membership are the seeded ledger and membership (B-23), and
 * the Saved list's counts are Maya's seeded list (`SampleSaved`, B-20), both of which the route reads too. One
 * part is the canvas's and not the server's, and is checked to be only that: the tiles of lines whose products the
 * seed does not sell, given by the canvas ([SampleOrders.TONES]). #HL-44019 is returned by its own refunded
 * return (B-21), as tracking reads it.
 *
 * The headers are the artboards': Maya with three in the cart, Sam and Jordan with none.
 */
class AccountFixturesTest {
    private val bodies = File(System.getProperty("haul.clientBodies") ?: error("haul.clientBodies is not set"))
    private val pretty = Json(haulWireJson) { prettyPrint = true }

    @Test
    fun `the client's account bodies are the trees the server builds`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                runBlocking {
                    val screen = world.koin.get<AccountScreen>()
                    val mismatches = mutableListOf<String>()

                    fun check(
                        name: String,
                        tree: KompotComponent,
                    ) {
                        val built = Json.parseToJsonElement(haulWireJson.encodeKompotComponent(tree))
                        val drawn =
                            File(bodies, name).takeIf { it.exists() }?.let { Json.parseToJsonElement(it.readText()) }
                        if (drawn != built) {
                            File("build/account-fixtures").apply { mkdirs() }.resolve(name).writeText(
                                pretty.encodeToString(JsonElement.serializer(), built) + "\n",
                            )
                            mismatches += name
                        }
                    }

                    /** [customer]'s account over [orders], with the canvas's tiles. */
                    suspend fun view(
                        customer: Customer,
                        orders: List<TrackedOrder>,
                    ): AccountView {
                        val view = screen.view(customer, orders)
                        val lines = orders.associate { it.order.id to it.order.placed.lines }
                        return view.copy(
                            orders =
                                view.orders.map { order ->
                                    val skus = lines.getValue(order.id).map { it.skuId }
                                    val tones =
                                        order.tones.zip(skus).map { (tone, sku) ->
                                            tone.ifEmpty {
                                                checkNotNull(
                                                    SampleOrders.TONES[sku],
                                                ) { "$sku: neither seeded nor the canvas's" }
                                            }
                                        }
                                    order.copy(tones = tones)
                                },
                        )
                    }

                    val mayas = checkNotNull(world.order(world.place(CheckoutChoice(slotId = WEDNESDAY_3PM)))).placed
                    val maya = Customer(SampleCustomers.MAYA, "Maya Kowalski", plus = true, joined = CatalogSeed.NOW)
                    val sam = Customer(SampleCustomers.SAM, "Sam Ortiz", plus = false, joined = CatalogSeed.NOW)
                    val jordan = Customer(JORDAN, "Jordan Lee", plus = false, joined = CatalogSeed.NOW)
                    val mayasView = view(maya, SampleOrders.mayasHistory(mayas))
                    assertEquals(
                        OrderState.Returned,
                        mayasView.orders.single { it.id == SampleOrders.RETURNED }.state,
                        "#HL-44019 is returned by its own refunded return (B-21)",
                    )
                    assertTrue(
                        mayasView.orders.none { it.card == null && it.state.active },
                        "every active order is drawn as a card",
                    )
                    val mayaLooking =
                        Viewer(firstName = "Maya", cartCount = 3, customerId = SampleCustomers.MAYA, customer = maya)

                    check(CONTENT, screen.page(AccountPage.Overview, mayasView, mayaLooking))
                    check(ORDERS, screen.page(AccountPage.Orders(), mayasView, mayaLooking))
                    check(
                        NOT_MEMBER,
                        screen.page(
                            AccountPage.Overview,
                            view(sam, SampleOrders.samsHistory(mayas)),
                            Viewer(firstName = "Sam", customerId = SampleCustomers.SAM, customer = sam),
                        ),
                    )
                    check(
                        NO_ORDERS,
                        screen.page(
                            AccountPage.Orders(),
                            view(jordan, emptyList()),
                            Viewer(firstName = "Jordan", customerId = JORDAN, customer = jordan),
                        ),
                    )

                    assertEquals(
                        emptyList(),
                        mismatches,
                        "bodies that are not the server's trees (see build/account-fixtures)",
                    )
                }
            }
        }

    private companion object {
        const val CONTENT = "account_content.json"
        const val ORDERS = "account_orders.json"
        const val NOT_MEMBER = "account_not_member.json"
        const val NO_ORDERS = "account_no_orders.json"

        const val WEDNESDAY_3PM = "2025-10-08T15"

        /** The canvas's customer with no orders (`Account_NoOrders`); not seeded — any new customer is Jordan. */
        const val JORDAN = "jordan"
    }
}
