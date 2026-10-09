package io.github.youndie.haul.feature.cart

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.cart.data.ExposedCartRepository
import io.github.youndie.haul.feature.cart.domain.CartCommands
import io.github.youndie.haul.feature.cart.domain.CartError
import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.cart.screen.CartScreen
import io.github.youndie.haul.feature.catalog.data.ExposedCatalogRepository
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.feature.identity.data.ExposedGuests
import io.github.youndie.haul.feature.identity.domain.Customer
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCatalog.DUVET_COVER
import io.github.youndie.haul.seed.SampleCatalog.SONY_HEADPHONES
import io.github.youndie.haul.seed.SampleCatalog.STONEWARE_MUG
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.shell.Viewer
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
import java.io.File
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The client draws the Cart screen's goldens from wire bodies (`composeApp/src/desktopTest/resources/
 * bodies/cart_*.json`), and this holds each one equal to the tree the server builds for the artboard's
 * cart — Maya's seeded cart at the canvas's «now», as Plus, with AUTUMN10, after SUMMER5, with the
 * mug's price changed, as a guest, and emptied.
 *
 * A body written by hand drifts from the wire without a sound: B-12 found a guest fixture that spelled
 * out a `null` the server leaves out, and the client refused every real guest's header while the
 * golden stayed green. Compared as JSON, a field the server omits, renames or adds fails here. A
 * mismatch writes what the server built to `build/cart-fixtures/`, which is the body to look at.
 *
 * One part is the canvas's and not the server's: the empty cart's picks. The canvas draws six products
 * the seed does not have (the home page's «Picked for you», feature-recommendations), so the body
 * keeps the canvas's cards and the comparison takes them from the body — after checking that each is
 * a card as the server writes one, key for key.
 */
class CartFixturesTest {
    private val dataSource: DataSource = seededFreshDatabase()
    private val database = Databases.connect(dataSource)
    private val catalog = ExposedCatalogRepository(database, CANVAS_NOW)
    private val carts = ExposedCartRepository(database)
    private val commands = CartCommands(carts, catalog, CANVAS_NOW)
    private val screen =
        CartScreen(carts, commands, catalog, DeliveryCalendar(CANVAS_NOW::now), CANVAS_NOW, ProductPhotos(null))
    private val maya = CartOwner.Customer(SampleCustomers.MAYA, plus = true)
    private val mayaLooking =
        Viewer(
            firstName = "Maya",
            customerId = SampleCustomers.MAYA,
            customer = Customer(SampleCustomers.MAYA, "Maya Kowalski", plus = true, joined = CatalogSeed.NOW),
        )
    private val bodies = File(System.getProperty("haul.clientBodies") ?: error("haul.clientBodies is not set"))
    private val pretty = Json(haulWireJson) { prettyPrint = true }

    private fun DataSource.sql(statement: String) {
        connection.use { c ->
            c.createStatement().use { it.executeUpdate(statement) }
            c.commit()
        }
    }

    private suspend fun mayas(): KompotComponent = screen.build(maya, mayaLooking)

    /** Every artboard's cart in one database of its own, in the order the states follow from each other. */
    @Test
    fun `the client's cart bodies are the trees the server builds`() =
        runBlocking {
            val mismatches = mutableListOf<String>()

            fun check(
                name: String,
                tree: KompotComponent,
                picksFromBody: Boolean = false,
            ) {
                val drawn = File(bodies, name).takeIf { it.exists() }?.let { parse(it.readText()) }
                var built = parse(haulWireJson.encodeKompotComponent(tree))
                if (picksFromBody && drawn != null) built = built.withPicksOf(drawn)
                if (drawn != built) {
                    File("build/cart-fixtures").apply { mkdirs() }.resolve(name).writeText(
                        pretty.encodeToString(JsonElement.serializer(), built) + "\n",
                    )
                    mismatches += name
                }
            }

            check("cart_content.json", mayas())

            commands.applyPromo(maya, "AUTUMN10")
            check("cart_promo_applied.json", mayas())
            commands.removePromo(maya)

            assertFailsWith<CartError.Promo> { commands.applyPromo(maya, "SUMMER5") }
            check("cart_promo_error.json", mayas())
            commands.removePromo(maya)

            dataSource.sql("UPDATE skus SET price_cents = 2600 WHERE id = '$STONEWARE_MUG-0'")
            check("cart_item_changed.json", mayas())
            dataSource.sql("UPDATE skus SET price_cents = 2400 WHERE id = '$STONEWARE_MUG-0'")

            val guest = CartOwner.Guest(ExposedGuests(database).create(CatalogSeed.NOW))
            listOf(SONY_HEADPHONES, DUVET_COVER, STONEWARE_MUG).forEach {
                commands.changeLine(guest, "$it-0", LineChange(quantity = 1))
            }
            check("cart_guest.json", screen.build(guest, Viewer()))

            commands.removeLines(maya, listOf("$SONY_HEADPHONES-0", "$DUVET_COVER-0", "$STONEWARE_MUG-0"))
            check("cart_empty.json", mayas(), picksFromBody = true)

            assertEquals(emptyList(), mismatches, "bodies that are not the server's trees (see build/cart-fixtures)")
        }

    private fun parse(json: String): JsonElement = Json.parseToJsonElement(json)

    /** This tree with the picks' cards of [body] in place of its own, once each is shaped as the server's are. */
    private fun JsonElement.withPicksOf(body: JsonElement): JsonElement {
        fun JsonElement.picks(): JsonObject =
            jsonObject
                .getValue("children")
                .jsonArray
                .single {
                    it.jsonObject["id"] ==
                        PICKED
                }.jsonObject
        val ours = picks().getValue("cards").jsonArray.map { it.jsonObject }
        // A card writes `oldPrice` and `badge` together, and only for a product under its old price (`card` in
        // Cards.kt). Since B-57 every pick, a live deal, is under one, so the shape of the same card at its own
        // price — the canvas's picks are — is the server's shape without the two.
        val shapes = ours.map { it.keys } + ours.map { it.keys - setOf("oldPrice", "badge") }
        val theirs = body.picks().getValue("cards").jsonArray
        theirs.forEach { card ->
            val keys = card.jsonObject.keys
            assertTrue(keys in shapes, "a pick no server card is shaped like: $card")
        }
        val children =
            jsonObject.getValue("children").jsonArray.map { child ->
                if (child.jsonObject["id"] == PICKED) JsonObject(child.jsonObject + ("cards" to theirs)) else child
            }
        return JsonObject(jsonObject + ("children" to JsonArray(children)))
    }

    private companion object {
        val PICKED = JsonPrimitive("picked")
    }
}
