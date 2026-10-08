package io.github.youndie.haul.feature.checkout

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.cart.data.ExposedCartRepository
import io.github.youndie.haul.feature.cart.domain.CartCommands
import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.catalog.data.ExposedCatalogRepository
import io.github.youndie.haul.feature.checkout.data.DeliverySlotsTable
import io.github.youndie.haul.feature.checkout.data.ExposedCheckoutRepository
import io.github.youndie.haul.feature.checkout.data.ExposedDeliverySlots
import io.github.youndie.haul.feature.checkout.domain.CheckoutCommands
import io.github.youndie.haul.feature.checkout.domain.CheckoutError
import io.github.youndie.haul.feature.checkout.screen.CheckoutScreen
import io.github.youndie.haul.feature.membership.data.ExposedPointsLedger
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.SampleCheckout
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.encodeKompotComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The client draws the Checkout screen's goldens from wire bodies (`composeApp/src/desktopTest/resources/
 * bodies/checkout_*.json`), and this holds each one equal to the tree the server builds for the artboard's
 * checkout — Maya's seeded cart and address at the canvas's «now», by courier on Wed 8 at 15:00, to the
 * nearest pickup point, to the nearest locker, with the address form refused, and with the window filled
 * after she chose it — each with the points toggle every artboard draws, off: «Use 2,480 points (−$24.80)»,
 * her seeded balance (research §6, B-23). `Checkout_PointsApplied` is the Content checkout with the toggle
 * turned on. The same reason as the cart's (`CartFixturesTest`): a body written by hand drifts from the
 * wire without a sound. A mismatch writes what the server built to `build/checkout-fixtures/`.
 */
class CheckoutFixturesTest {
    private val bodies = File(System.getProperty("haul.clientBodies") ?: error("haul.clientBodies is not set"))
    private val pretty = Json(haulWireJson) { prettyPrint = true }
    private val maya = CartOwner.Customer(SampleCustomers.MAYA, plus = true)

    @Test
    fun `the client's checkout bodies are the trees the server builds`() =
        seededFreshDatabase().use { dataSource ->
            runBlocking {
                val database = Databases.connect(dataSource)
                val carts = ExposedCartRepository(database)
                val commands =
                    CheckoutCommands(
                        ExposedCheckoutRepository(database),
                        ExposedDeliverySlots(database),
                        carts,
                        CartCommands(carts, ExposedCatalogRepository(database), CANVAS_NOW),
                        ExposedPointsLedger(database),
                        CANVAS_NOW,
                    )
                val screen = CheckoutScreen(commands)
                val mismatches = mutableListOf<String>()

                fun check(
                    name: String,
                    tree: JsonElement,
                ) {
                    val drawn =
                        File(
                            bodies,
                            name,
                        ).takeIf { it.exists() }?.let { Json.parseToJsonElement(it.readText()) }
                    if (drawn != tree) {
                        File("build/checkout-fixtures").apply { mkdirs() }.resolve(name).writeText(
                            pretty.encodeToString(JsonElement.serializer(), tree) + "\n",
                        )
                        mismatches += name
                    }
                }

                suspend fun built(): JsonElement = parse(screen.build(maya))

                commands.choose(maya, CheckoutChoice(slotId = "2025-10-08T15"))
                check(CONTENT, built())
                commands.choose(maya, CheckoutChoice(usePoints = true))
                check(POINTS_APPLIED, built())
                commands.choose(maya, CheckoutChoice(usePoints = false))

                commands.choose(maya, CheckoutChoice(method = DeliveryMethod.PickupPoint))
                check(PICKUP_POINT, built())

                commands.choose(maya, CheckoutChoice(method = DeliveryMethod.ParcelLocker))
                check(PARCEL_LOCKER, built())

                commands.choose(maya, CheckoutChoice(method = DeliveryMethod.Courier))
                assertFailsWith<CheckoutError.AddressRefused> {
                    commands.saveAddress(maya, AddressEntry(street = "", apt = "4F", city = "Brooklyn, NY", zip = ""))
                }
                check(VALIDATION, built())

                commands.choose(maya, CheckoutChoice(addressId = SampleCheckout.MAYA_ADDRESS))
                transaction(database) {
                    DeliverySlotsTable.upsert {
                        it[DeliverySlotsTable.day] = LocalDate.parse("2025-10-08")
                        it[startHour] = 15
                        it[capacity] = 20
                        it[taken] = 20
                    }
                }
                check(PLACE_ERROR, built())

                assertEquals(
                    emptyList(),
                    mismatches,
                    "bodies that are not the server's trees (see build/checkout-fixtures)",
                )
            }
        }

    private fun parse(tree: KompotComponent): JsonElement =
        Json.parseToJsonElement(haulWireJson.encodeKompotComponent(tree))

    private companion object {
        const val CONTENT = "checkout_content.json"
        const val POINTS_APPLIED = "checkout_points_applied.json"
        const val PICKUP_POINT = "checkout_pickup_point.json"
        const val PARCEL_LOCKER = "checkout_parcel_locker.json"
        const val VALIDATION = "checkout_validation.json"
        const val PLACE_ERROR = "checkout_place_error.json"
    }
}
