package io.github.youndie.haul.feature.saved

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.feature.account.AccountPaths
import io.github.youndie.haul.feature.cart.CartPaths
import io.github.youndie.haul.feature.saved.screen.SavedFilter
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.SampleCatalog.SONY_HEADPHONES
import io.github.youndie.haul.seed.SampleCatalog.STONEWARE_MUG
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.testing.ShildikHarness
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.assertError
import io.github.youndie.haul.testing.assertRefresh
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.ui.AccountBody
import io.github.youndie.haul.ui.AccountTileKind
import io.github.youndie.haul.ui.CartLine
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductDetails
import io.github.youndie.haul.ui.SavedList
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Saved list over HTTP (feature-account, screen-saved, endpoint-saved, B-20), against shildik and a
 * seeded PostgreSQL of each test's own: Maya's seeded list of 48, six of them cheaper than when she saved
 * them; the heart's `PUT` and `DELETE`; «Save for later» from the cart; and the list's page, filtered and
 * paged by its address.
 */
class SavedRoutesTest {
    private val maya by lazy {
        ShildikHarness.accessToken(ShildikHarness.person("Maya Kowalski", SampleCustomers.MAYA))
    }
    private val sam by lazy { ShildikHarness.accessToken(ShildikHarness.person("Sam Ortiz", SampleCustomers.SAM)) }

    private fun world(block: suspend HttpClient.(DataSource) -> Unit) =
        seededFreshDatabase().use { database ->
            haulTest(database, signIn = ShildikHarness.signIn) { block(database) }
        }

    private fun DataSource.sql(statement: String) {
        connection.use { c ->
            c.createStatement().use { it.executeUpdate(statement) }
            c.commit()
        }
    }

    private suspend fun HttpClient.tree(
        token: String?,
        path: String,
    ): KompotComponent {
        val response = get(path) { token?.let { bearerAuth(it) } }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return haulWireJson.decodeKompotComponent(response.bodyAsText())
    }

    private suspend fun HttpClient.saved(
        token: String,
        path: String = SavedPaths.SCREEN,
    ): AccountBody = tree(token, path).only<AccountBody>()

    private fun AccountBody.list(): SavedList = checkNotNull(saved) { "no Saved list on $title" }

    /** The product ids of every card on every page of [token]'s list, newest first. */
    private suspend fun HttpClient.everything(token: String): List<String> {
        val first = saved(token).list()
        val pages = first.pagination?.pages?.size ?: 1
        return first.cards.map { it.productId } +
            (2..pages).flatMap { page ->
                saved(token, SavedPaths.SCREEN + "?page=$page").list().cards.map { it.productId }
            }
    }

    /**
     * feature-account, «Saved twice»: the second `PUT` of the same product answers `200` too, and the list
     * holds it once — at the price and the day of the first save, not of the second.
     */
    @Test
    fun `a product saved twice is in the list once`() =
        world { database ->
            val path = SavedPaths.item(STONEWARE_MUG)
            put(path) { bearerAuth(maya) }.assertRefresh()
            database.sql("UPDATE skus SET price_cents = 2000 WHERE id = '$STONEWARE_MUG-0'")
            put(path) { bearerAuth(maya) }.assertRefresh()

            val all = everything(maya)
            assertEquals(1, all.count { it == STONEWARE_MUG })
            assertEquals(STONEWARE_MUG, all.first(), "the newest save is first")
            assertEquals(49, all.size)
            val mug = saved(maya).list().cards.first()
            assertEquals("Price dropped −$4", mug.drop, "the second save moved the price it is compared with")
            assertEquals("49", saved(maya).count)
        }

    /** The heart's `DELETE` lets the product go; one that is not in the list is already gone, and says `200`. */
    @Test
    fun `a product let go leaves the list and a second let-go changes nothing`() =
        world {
            delete(SavedPaths.item(SONY_HEADPHONES)) { bearerAuth(maya) }.assertRefresh()
            delete(SavedPaths.item(SONY_HEADPHONES)) { bearerAuth(maya) }.assertRefresh()
            val all = everything(maya)
            assertEquals(47, all.size)
            assertTrue(SONY_HEADPHONES !in all)
            val body = saved(maya)
            assertEquals(
                listOf("All" to "47", "Price dropped" to "5"),
                body.list().filters.map { it.label to it.count },
            )
        }

    /**
     * feature-account, «Price drop counted»: Maya saved the Robot Vacuum S8 at $499, and it now costs $299
     * — the account's «Price drops» counts it, and the Saved list marks it «Price dropped −$200», under «All»
     * and under «Price dropped».
     */
    @Test
    fun `a product cheaper than on the day it was saved is counted and marked`() =
        world { database ->
            database.sql(
                """
                INSERT INTO products (id, seller_id, category_slug, title, brand, description, specifications,
                    rating, reviews_count, questions_count, tone, label, created_at, headline)
                VALUES ('$VACUUM', 's-brooklyn-home-co', 'cookware', 'Robot Vacuum S8 with Self-Empty Dock', 'Haul',
                    'A robot vacuum.', '[]', 4.5, 3377, 0, '#E0EEF7', 'vacuum', '2025-09-01T00:00:00Z', 'Clean floors')
                """.trimIndent(),
            )
            database.sql("INSERT INTO skus VALUES ('$VACUUM-0', '$VACUUM', 0, '{}', 49900, NULL, 12)")
            val before = tree(maya, AccountPaths.SCREEN).only<AccountBody>()
            assertEquals("6", before.tiles.single { it.kind == AccountTileKind.PriceDrops }.figure)

            put(SavedPaths.item(VACUUM)) { bearerAuth(maya) }.assertRefresh()
            assertNull(
                saved(maya)
                    .list()
                    .cards
                    .first()
                    .drop,
                "saved at its price, it has not dropped",
            )
            database.sql("UPDATE skus SET price_cents = 29900 WHERE id = '$VACUUM-0'")

            val after = tree(maya, AccountPaths.SCREEN).only<AccountBody>()
            assertEquals("7", after.tiles.single { it.kind == AccountTileKind.PriceDrops }.figure)
            val first = saved(maya).list().cards.first()
            assertEquals(VACUUM to "Price dropped −$200", first.productId to first.drop)
            assertEquals("$299", first.price)
            val dropped = saved(maya, "/ui" + SavedPaths.page(SavedFilter.PriceDropped)).list()
            assertEquals(7, dropped.cards.size)
            assertEquals(VACUUM, dropped.cards.first().productId)
            assertTrue(dropped.cards.all { it.drop != null }, "every card under «Price dropped» is marked")

            database.sql("UPDATE skus SET stock = 0 WHERE id = '$VACUUM-0'")
            assertNull(
                saved(maya)
                    .list()
                    .cards
                    .first()
                    .drop,
                "a product with nothing in stock has no price to have dropped to",
            )
        }

    /**
     * The page is the account's (screen-saved): «Saved» with the count, selected in the menu; 24 newest
     * first to a page, the pages by `?page=`, the filter by `?filter=price-dropped` — each chip and each
     * page number its own address. A filter the list does not have is all of them; a page past the last is
     * the last; a page that is not a number from 1 is `400 validation_failed`.
     */
    @Test
    fun `the list is paged and filtered by its address`() =
        world {
            val body = saved(maya)
            assertEquals("Saved" to "48", body.title to body.count)
            val menu = body.menu.single { it.label == "Saved" }
            assertEquals(
                Triple("48", true, NavigateAction(Frame.SAVED)),
                Triple(menu.count, menu.selected, menu.action),
            )
            val list = body.list()
            assertEquals(24, list.cards.size)
            assertEquals(
                listOf(
                    Triple("All", "48", NavigateAction("/account/saved")),
                    Triple("Price dropped", "6", NavigateAction("/account/saved?filter=price-dropped")),
                ),
                list.filters.map { Triple(it.label, it.count, it.action) },
            )
            assertEquals(listOf("All"), list.filters.filter { it.selected }.map { it.label })
            assertTrue(list.cards.all { it.saved && it.heartCommand?.save == false }, "every heart lets its product go")
            assertEquals(SONY_HEADPHONES to "Price dropped −$100", list.cards[1].productId to list.cards[1].drop)
            val pagination = checkNotNull(list.pagination)
            assertEquals(listOf("1", "2"), pagination.pages)
            assertNull(pagination.moreLabel)
            assertEquals(listOf(NavigateAction("/account/saved?page=2")), pagination.links.map { it.action })

            val second = saved(maya, "${SavedPaths.SCREEN}?page=2").list()
            assertEquals(24, second.cards.size)
            assertEquals(48, (list.cards + second.cards).map { it.productId }.toSet().size)
            assertEquals(2, second.pagination?.current)

            val dropped = saved(maya, "${SavedPaths.SCREEN}?filter=price-dropped").list()
            assertEquals(6, dropped.cards.size)
            assertNull(dropped.pagination)
            assertEquals(listOf("Price dropped"), dropped.filters.filter { it.selected }.map { it.label })

            assertEquals(
                48,
                saved(maya, "${SavedPaths.SCREEN}?filter=cheap")
                    .list()
                    .filters[0]
                    .count
                    .toInt(),
            )
            assertEquals(
                second.cards,
                saved(maya, "${SavedPaths.SCREEN}?page=9").list().cards,
                "a page past the last is the last",
            )
            get("${SavedPaths.SCREEN}?page=0") { bearerAuth(maya) }
                .assertError(HttpStatusCode.BadRequest, ErrorCode.ValidationFailed)
        }

    /**
     * A list is its customer's own: Sam, over the same database as Maya's 48, has nothing saved — the empty
     * state with the way to the deals and how the heart works, no count in the menu — and what he saves is
     * not Maya's.
     */
    @Test
    fun `a customer sees only their own list`() =
        world {
            val sams = saved(sam)
            assertNull(sams.count)
            assertNull(sams.menu.single { it.label == "Saved" }.count)
            val empty = sams.list()
            assertEquals("Nothing saved yet", empty.empty?.title)
            assertEquals(NavigateAction(Frame.DEALS), empty.empty?.action)
            assertEquals(3, empty.steps.size)
            assertEquals(emptyList(), empty.cards)

            put(SavedPaths.item(STONEWARE_MUG)) { bearerAuth(sam) }.assertRefresh()
            assertEquals(listOf(STONEWARE_MUG), saved(sam).list().cards.map { it.productId })
            assertTrue(STONEWARE_MUG !in everything(maya), "Sam's save is not in Maya's list")
        }

    /**
     * «Save for later» moves a cart line to the Saved list (B-20): the mug leaves Maya's cart and is the
     * newest in her list; a second press finds no line (`404 line_not_found`) and changes nothing.
     */
    @Test
    fun `save for later moves a line from the cart to the list`() =
        world {
            val line =
                tree(maya, CartPaths.SCREEN).all().filterIsInstance<CartLine>().single {
                    it.productId ==
                        STONEWARE_MUG
                }
            val url = checkNotNull(line.saveUrl)
            assertEquals(CartPaths.saveForLater("$STONEWARE_MUG-0"), url)
            assertNull(line.saveAction)

            post(url) { bearerAuth(maya) }.assertRefresh()
            val cart = tree(maya, CartPaths.SCREEN).all().filterIsInstance<CartLine>()
            assertEquals(listOf(SONY_HEADPHONES, "p-linen-duvet-cover-set"), cart.map { it.productId })
            assertEquals(
                STONEWARE_MUG,
                saved(maya)
                    .list()
                    .cards
                    .first()
                    .productId,
            )

            post(url) { bearerAuth(maya) }.assertError(HttpStatusCode.NotFound, ErrorCode.LineNotFound)
            assertEquals(49, everything(maya).size)
        }

    /** The heart of a product the catalog does not have is `404 product_not_found`, and saves nothing. */
    @Test
    fun `an unknown product cannot be saved`() =
        world {
            put(SavedPaths.item("p-nowhere")) { bearerAuth(maya) }
                .assertError(HttpStatusCode.NotFound, ErrorCode.ProductNotFound)
            assertEquals("48", saved(maya).count)
        }

    /**
     * The hearts everywhere are the viewer's (B-20): on Maya's home page a product she saved is drawn filled
     * and its heart lets it go, one she did not save keeps it; on the product page too. A guest's hearts, the
     * header's «Saved» and a guest's «Save for later» are the way to sign in; a customer's «Saved» is the list.
     */
    @Test
    fun `hearts are commands for a customer and the way to sign in for a guest`() =
        world {
            val home = tree(maya, "/ui/home")
            val cards = home.all().filterIsInstance<ProductCard>()
            val sony = cards.first { it.productId == SONY_HEADPHONES }
            assertTrue(sony.saved)
            assertEquals(SaveCommand(SavedPaths.item(SONY_HEADPHONES), save = false), sony.heartCommand)
            val unsaved = cards.first { !it.saved }
            assertEquals(SaveCommand(SavedPaths.item(unsaved.productId), save = true), unsaved.heartCommand)
            assertTrue(cards.all { it.heartAction == null })
            assertEquals(NavigateAction(Frame.SAVED), home.only<HaulHeader>().saved)

            val details = tree(maya, "/ui/p/$STONEWARE_MUG").only<ProductDetails>()
            assertEquals(
                false to SaveCommand(SavedPaths.item(STONEWARE_MUG), save = true),
                details.saved to details.heartCommand,
            )

            val guests = tree(null, "/ui/home")
            val guestCards = guests.all().filterIsInstance<ProductCard>()
            assertTrue(guestCards.isNotEmpty())
            assertTrue(
                guestCards.all {
                    !it.saved && it.heartCommand == null &&
                        it.heartAction == NavigateAction(Frame.SIGN_IN)
                },
            )
            assertEquals(NavigateAction(Frame.SIGN_IN), guests.only<HaulHeader>().saved)
            assertNotNull(tree(null, "/ui/p/$STONEWARE_MUG").only<ProductDetails>().heartAction)
        }

    /** The list and its commands are the customer tier's: without a token, `401 unauthenticated`. */
    @Test
    fun `the list and its commands need a sign-in`() =
        world {
            get(SavedPaths.SCREEN).assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
            put(SavedPaths.item(STONEWARE_MUG)).assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
            delete(SavedPaths.item(STONEWARE_MUG)).assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
            post(CartPaths.saveForLater("$STONEWARE_MUG-0"))
                .assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
        }

    private companion object {
        /** feature-account's robot vacuum, which the seed does not sell: the scenario adds it. */
        const val VACUUM = "p-robot-vacuum-s8"
    }
}
