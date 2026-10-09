package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.feature.cart.screen.CartScreen
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.testing.SeededDatabase
import io.github.youndie.haul.testing.ShildikHarness
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.cart
import io.github.youndie.haul.testing.guest
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.CampaignRow
import io.github.youndie.haul.ui.EmptyState
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.SectionHeader
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What announces the sale is drawn only while there is a sale to announce (B-59), on the store's clock: home's
 * hero while the Autumn mega sale is live for the viewer, each banner until its campaign ends, the empty
 * cart's «Today's deals end at midnight — up to −70 % in the Autumn mega sale.» while a deal and the sale
 * are both live.
 *
 * Before B-59 all three were drawn for good: past the sale's end (Oct 15, 2025) home announced «Up to −70%»
 * beside «Laptops from $399» whose week was over, and the empty cart promised deals and −70 % on a stand that
 * had neither (B-58's findings). In the seed Tech week and the free-delivery weekend end on Oct 13, the sale
 * on Oct 15, the deals at the canvas day's midnight.
 */
class SaleAnnouncementsTest {
    private val maya by lazy {
        ShildikHarness.accessToken(ShildikHarness.person("Maya Kowalski", SampleCustomers.MAYA))
    }

    /** The store's «now», which a test moves across the campaigns' ends. */
    private class MovingClock(
        var at: OffsetDateTime,
    ) : StoreClock {
        override fun now(): ZonedDateTime = at.toZonedDateTime()
    }

    private fun store(
        database: DataSource = SeededDatabase.dataSource,
        block: suspend HttpClient.(clock: MovingClock) -> Unit,
    ) {
        val clock = MovingClock(CatalogSeed.NOW)
        haulTest(database, signIn = ShildikHarness.signIn, clock = clock) { block(clock) }
    }

    private fun KompotComponent.row(): CampaignRow? = all().filterIsInstance<CampaignRow>().singleOrNull()

    private fun KompotComponent.ids(): Set<String> =
        all()
            .mapNotNull {
                when (it) {
                    is SectionHeader -> it.id
                    is ProductGrid -> it.id
                    else -> null
                }
            }.toSet()

    private suspend fun HttpClient.home(token: String): KompotComponent {
        val response = get("/ui/home") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return haulWireJson.decodeKompotComponent(response.bodyAsText())
    }

    /**
     * A banner is drawn until its campaign ends — the free-delivery weekend before it opens too, as the canvas
     * draws it on Oct 7 — and from its end it is gone while the sale's hero stays: a minute before Oct 13 both
     * banners are there, at midnight neither.
     */
    @Test
    fun `a banner is drawn until its campaign ends`() =
        store { clock ->
            val canvas = assertNotNull(tree("/ui/home").row(), "the control: the canvas's home draws its row")
            assertEquals(BANNERS, canvas.banners.map { it.id })

            clock.at = BANNERS_END.minusMinutes(1)
            assertEquals(BANNERS, assertNotNull(tree("/ui/home").row()).banners.map { it.id }, "a minute before")

            clock.at = BANNERS_END
            val row = assertNotNull(tree("/ui/home").row(), "the sale's hero went with its banners")
            assertEquals(emptyList(), row.banners.map { it.id }, "a banner of an ended campaign is drawn")
            assertEquals("campaign-autumn-mega-sale", row.hero.id)
        }

    /**
     * From the sale's end home draws no hero and no row at all — nothing announces «Up to −70%» whose prices
     * are gone — while the rest of the page stays; a minute before, the hero is still there.
     */
    @Test
    fun `the hero is gone once the sale ends`() =
        store { clock ->
            clock.at = SALE_END.minusMinutes(1)
            assertNotNull(tree("/ui/home").row(), "the control: the sale's last minute draws the hero")

            clock.at = SALE_END
            val home = tree("/ui/home")
            assertNull(home.row(), "an ended sale is still announced")
            assertTrue("categories-title" in home.ids(), "home lost more than its hero")
        }

    /**
     * The hero is drawn while the sale is live for the viewer: on Oct 6 it is open to Plus members only, so
     * Maya sees it and a guest does not — Tech week is live that day, but the row is the hero's and goes
     * with it.
     */
    @Test
    fun `the hero waits for the sale to open for the viewer`() =
        store { clock ->
            clock.at = EARLY_ACCESS
            assertNull(tree("/ui/home").row(), "a guest is announced a sale not open to him")
            val mayas = assertNotNull(home(maya).row(), "Maya's early access does not draw the hero")
            assertEquals("campaign-autumn-mega-sale", mayas.hero.id)
        }

    /**
     * The empty cart's sale line names today's deals and the sale, and is drawn only while both are live: at the
     * canvas's «now» it is; from the deals' end (the sale still live) it is not, the title and «See today's
     * deals» staying.
     */
    @Test
    fun `the empty cart's sale line needs a live deal`() =
        store { clock ->
            val guest = guest()
            assertEquals(CartScreen.SALE_LINE, cart(guest).only<EmptyState>().text, "the control: the canvas's line")

            clock.at = CatalogSeed.DEALS_END
            val empty = cart(guest).only<EmptyState>()
            assertNull(empty.text, "the line promises deals that have ended")
            assertEquals("Your cart is empty", empty.title)
            assertEquals(NavigateAction("/deals"), empty.action)
        }

    /**
     * With the deals moved past the sale's end, from the sale's end the empty cart draws no sale line though its
     * picks — today's deals — are still there, and home draws its deals but no hero.
     */
    @Test
    fun `the empty cart's sale line needs a live sale`() =
        seededFreshDatabase().use { database ->
            database.connection.use { c ->
                c.createStatement().use { it.executeUpdate("UPDATE deals SET ends_at = '$AFTER_SALE'") }
                c.commit()
            }
            store(database) { clock ->
                clock.at = SALE_END
                val guest = guest()
                val empty = cart(guest)
                assertTrue(PICKS.all { it in empty.ids() }, "the control: the deals are live and picked")
                assertNull(empty.only<EmptyState>().text, "the line promises −70 % in an ended sale")

                val home = tree("/ui/home")
                assertTrue("deals" in home.ids(), "the control: home draws the live deals")
                assertNull(home.row(), "an ended sale is announced beside live deals")
            }
        }

    private companion object {
        val BANNERS = listOf("banner-tech-week", "banner-free-delivery-weekend")
        val PICKS = setOf("picked-title", "picked")
        val EARLY_ACCESS: OffsetDateTime = OffsetDateTime.parse("2025-10-06T12:00:00-04:00")
        val BANNERS_END: OffsetDateTime = OffsetDateTime.parse("2025-10-13T00:00:00-04:00")
        val SALE_END: OffsetDateTime = OffsetDateTime.parse("2025-10-15T00:00:00-04:00")
        val AFTER_SALE: OffsetDateTime = OffsetDateTime.parse("2025-10-16T00:00:00-04:00")
    }
}
