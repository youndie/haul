package io.github.youndie.haul.feature.saved

import io.github.youndie.haul.feature.account.domain.SavedSummary
import io.github.youndie.haul.feature.account.screen.AccountPage
import io.github.youndie.haul.feature.account.screen.AccountScreen
import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.identity.domain.Customer
import io.github.youndie.haul.feature.saved.screen.SavedFilter
import io.github.youndie.haul.feature.saved.screen.SavedScreen
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The client draws the Saved list's goldens from wire bodies (`composeApp/src/desktopTest/resources/
 * bodies/saved_*.json`), and this holds each one equal to the tree the server builds for Maya — her seeded
 * list of 48 with 6 price drops (`seed/SampleSaved.kt`), her orders as the account draws them, three in the
 * cart — the same reason as the account's (`AccountFixturesTest`). A mismatch writes what the server built
 * to `build/saved-fixtures/`.
 *
 * One part is the canvas's and not the server's: the cards. The canvas draws products the seed does not
 * sell (the robot vacuum, the keyboard, the trainers, …), so the bodies keep the canvas's twelve and six
 * cards and the comparison takes them from the body — after checking that each is a card as the server
 * writes one on that page, key for key, and that the body has no more cards than the server's page. The
 * Empty artboard's menu still reads «Saved 48»; the server draws an empty list without a count.
 */
class SavedFixturesTest {
    private val bodies = File(System.getProperty("haul.clientBodies") ?: error("haul.clientBodies is not set"))
    private val pretty = Json(haulWireJson) { prettyPrint = true }

    @Test
    fun `the client's saved bodies are the trees the server builds`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                runBlocking {
                    val screen = world.koin.get<AccountScreen>()
                    val saved = world.koin.get<SavedScreen>()
                    val mismatches = mutableListOf<String>()

                    fun check(
                        name: String,
                        tree: KompotComponent,
                    ) {
                        var built = Json.parseToJsonElement(haulWireJson.encodeKompotComponent(tree))
                        val drawn =
                            File(bodies, name).takeIf { it.exists() }?.let { Json.parseToJsonElement(it.readText()) }
                        if (drawn != null) built = built.withCardsOf(drawn)
                        if (drawn != built) {
                            File("build/saved-fixtures").apply { mkdirs() }.resolve(name).writeText(
                                pretty.encodeToString(JsonElement.serializer(), built) + "\n",
                            )
                            mismatches += name
                        }
                    }

                    val mayas = checkNotNull(world.order(world.place(CheckoutChoice(slotId = WEDNESDAY_3PM)))).placed
                    val maya = Customer(SampleCustomers.MAYA, "Maya Kowalski", plus = true, joined = CatalogSeed.NOW)
                    val view = screen.view(maya, SampleOrders.mayasHistory(mayas))
                    assertEquals(SavedSummary(saved = 48, priceDrops = 6), view.saved, "Maya's seeded list")
                    val looking =
                        Viewer(firstName = "Maya", cartCount = 3, customerId = SampleCustomers.MAYA, customer = maya)

                    val all = saved.list(SampleCustomers.MAYA, SavedFilter.All, 1, looking)
                    check(CONTENT, screen.page(AccountPage.Saved(), view.copy(list = all), looking))
                    val dropped = saved.list(SampleCustomers.MAYA, SavedFilter.PriceDropped, 1, looking)
                    check(
                        PRICE_DROPS,
                        screen.page(AccountPage.Saved(SavedFilter.PriceDropped), view.copy(list = dropped), looking),
                    )
                    val nothing = saved.list(emptyList(), SavedFilter.All, 1, looking)
                    check(
                        EMPTY,
                        screen.page(
                            AccountPage.Saved(),
                            view.copy(saved = SavedSummary(0, 0), list = nothing),
                            looking,
                        ),
                    )

                    assertEquals(
                        emptyList(),
                        mismatches,
                        "bodies that are not the server's trees (see build/saved-fixtures)",
                    )
                }
            }
        }

    /** This tree with the Saved list's cards of [body] in place of its own, once each is shaped as the server's are. */
    private fun JsonElement.withCardsOf(body: JsonElement): JsonElement {
        fun JsonElement.account(): JsonObject =
            jsonObject
                .getValue("children")
                .jsonArray
                .single { it.jsonObject["type"] == ACCOUNT_BODY }
                .jsonObject

        fun JsonObject.cards(): JsonArray? = (this["saved"] as? JsonObject)?.get("cards")?.jsonArray

        val ours = account().cards() ?: return this
        val theirs = body.account().cards() ?: return this
        assertTrue(theirs.size <= ours.size, "the body draws more cards than the server's page has")
        theirs.forEach { card ->
            val keys = card.jsonObject.keys
            assertTrue(ours.any { it.jsonObject.keys == keys }, "a card no server card is shaped like: $card")
        }
        val account = account()
        val list = JsonObject(account.getValue("saved").jsonObject + ("cards" to theirs))
        val children =
            jsonObject.getValue("children").jsonArray.map { child ->
                if (child.jsonObject["type"] == ACCOUNT_BODY) JsonObject(account + ("saved" to list)) else child
            }
        return JsonObject(jsonObject + ("children" to JsonArray(children)))
    }

    private companion object {
        const val CONTENT = "saved_content.json"
        const val PRICE_DROPS = "saved_price_drops.json"
        const val EMPTY = "saved_empty.json"

        const val WEDNESDAY_3PM = "2025-10-08T15"
        val ACCOUNT_BODY = JsonPrimitive("haul_account_body")
    }
}
