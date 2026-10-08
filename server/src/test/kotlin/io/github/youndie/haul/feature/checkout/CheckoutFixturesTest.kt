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
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.SampleCheckout
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.encodeKompotComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
 * after she chose it. The same reason as the cart's (`CartFixturesTest`): a body written by hand drifts
 * from the wire without a sound. A mismatch writes what the server built to `build/checkout-fixtures/`.
 *
 * Two parts are the canvas's and not the server's, both feature-membership's (B-23), which stores no
 * points balance yet:
 * - the points toggle every artboard draws under the ways to pay, off — «Use 2,480 points (−$24.80)»,
 *   Maya's balance in research §6. The server sends none, so each body's toggle is taken from the body
 *   after checking it is exactly that one, with no address to send a change to.
 * - `Checkout_PointsApplied`: the toggle on, the points row, the total and the button with the points
 *   taken off, and Haul Pay's four payments of the lower total. Its body is the Content tree with those
 *   changes and no others, which is what this checks.
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

                /** The tree with the toggle the body draws, once it is the canvas's toggle, off. */
                fun withCanvasToggle(
                    name: String,
                    tree: JsonElement,
                ): JsonElement {
                    val toggle =
                        File(
                            bodies,
                            name,
                        ).takeIf { it.exists() }?.let { body(Json.parseToJsonElement(it.readText())) }
                    toggle?.let {
                        assertEquals(
                            CANVAS_TOGGLE,
                            it.payment()["points"],
                            "$name: not the canvas's toggle",
                        )
                    }
                    return tree.withBody {
                        it.withPayment { payment ->
                            JsonObject(payment + ("points" to CANVAS_TOGGLE))
                        }
                    }
                }

                commands.choose(maya, CheckoutChoice(slotId = "2025-10-08T15"))
                val content = withCanvasToggle(CONTENT, built())
                check(CONTENT, content)
                check(POINTS_APPLIED, content.pointsApplied())

                commands.choose(maya, CheckoutChoice(method = DeliveryMethod.PickupPoint))
                check(PICKUP_POINT, withCanvasToggle(PICKUP_POINT, built()))

                commands.choose(maya, CheckoutChoice(method = DeliveryMethod.ParcelLocker))
                check(PARCEL_LOCKER, withCanvasToggle(PARCEL_LOCKER, built()))

                commands.choose(maya, CheckoutChoice(method = DeliveryMethod.Courier))
                assertFailsWith<CheckoutError.AddressRefused> {
                    commands.saveAddress(maya, AddressEntry(street = "", apt = "4F", city = "Brooklyn, NY", zip = ""))
                }
                check(VALIDATION, withCanvasToggle(VALIDATION, built()))

                commands.choose(maya, CheckoutChoice(addressId = SampleCheckout.MAYA_ADDRESS))
                transaction(database) {
                    DeliverySlotsTable.upsert {
                        it[DeliverySlotsTable.day] = LocalDate.parse("2025-10-08")
                        it[startHour] = 15
                        it[capacity] = 20
                        it[taken] = 20
                    }
                }
                check(PLACE_ERROR, withCanvasToggle(PLACE_ERROR, built()))

                assertEquals(
                    emptyList(),
                    mismatches,
                    "bodies that are not the server's trees (see build/checkout-fixtures)",
                )
            }
        }

    private fun parse(tree: KompotComponent): JsonElement =
        Json.parseToJsonElement(haulWireJson.encodeKompotComponent(tree))

    private fun body(tree: JsonElement): JsonObject =
        tree.jsonObject
            .getValue("children")
            .jsonArray
            .single { it.jsonObject["type"] == JsonPrimitive("haul_checkout_body") }
            .jsonObject

    private fun JsonObject.payment(): JsonObject = getValue("payment").jsonObject

    private fun JsonElement.withBody(change: (JsonObject) -> JsonObject): JsonElement {
        val children =
            jsonObject.getValue("children").jsonArray.map {
                if (it.jsonObject["type"] == JsonPrimitive("haul_checkout_body")) change(it.jsonObject) else it
            }
        return JsonObject(jsonObject + ("children" to JsonArray(children)))
    }

    private fun JsonObject.withPayment(change: (JsonObject) -> JsonObject): JsonObject =
        JsonObject(this + ("payment" to change(payment())))

    /**
     * `Checkout_PointsApplied` over Content: the canvas's notes — «summary line «Points −$24.80», total
     * $487.20, «Place order · $487.20», Haul Pay 4 × $121.80» — and the toggle on.
     */
    private fun JsonElement.pointsApplied(): JsonElement =
        withBody { body ->
            val payment =
                body.withPayment { payment ->
                    val options =
                        payment.getValue("options").jsonArray.map { option ->
                            val o = option.jsonObject
                            if (o.getValue("id").jsonPrimitive.content == "haul_pay") {
                                JsonObject(o + ("detail" to JsonPrimitive("4 payments of $121.80")))
                            } else {
                                o
                            }
                        }
                    JsonObject(
                        payment + ("options" to JsonArray(options)) +
                            ("points" to JsonObject(CANVAS_TOGGLE + ("on" to JsonPrimitive(true)))),
                    )
                }
            val summary = body.getValue("summary").jsonObject
            val rows = summary.getValue("rows").jsonArray.toMutableList()
            rows.add(
                2,
                JsonObject(
                    mapOf(
                        "label" to JsonPrimitive("Points"),
                        "value" to JsonPrimitive("−$24.80"),
                        "saving" to JsonPrimitive(true),
                    ),
                ),
            )
            JsonObject(
                payment +
                    (
                        "summary" to
                            JsonObject(
                                summary + ("rows" to JsonArray(rows)) + ("total" to JsonPrimitive("$487.20")) +
                                    ("placeLabel" to JsonPrimitive("Place order · $487.20")),
                            )
                    ),
            )
        }

    private companion object {
        const val CONTENT = "checkout_content.json"
        const val POINTS_APPLIED = "checkout_points_applied.json"
        const val PICKUP_POINT = "checkout_pickup_point.json"
        const val PARCEL_LOCKER = "checkout_parcel_locker.json"
        const val VALIDATION = "checkout_validation.json"
        const val PLACE_ERROR = "checkout_place_error.json"

        /** The toggle every Checkout artboard draws, off (research §6: Maya's 2,480 points). */
        val CANVAS_TOGGLE =
            JsonObject(
                mapOf(
                    "label" to JsonPrimitive("Use 2,480 points (−$24.80)"),
                    "detail" to JsonPrimitive("100 points = $1 · all or nothing"),
                    "on" to JsonPrimitive(false),
                ),
            )
    }
}
