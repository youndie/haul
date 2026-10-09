package io.github.youndie.haul.seed

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.PostgresHarness
import io.github.youndie.haul.testing.SeededDatabase
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.applyPromo
import io.github.youndie.haul.testing.assertError
import io.github.youndie.haul.testing.assertRefresh
import io.github.youndie.haul.testing.guest
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.putLine
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.CampaignHero
import io.github.youndie.haul.ui.PageTitle
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductDetails
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.SectionHeader
import io.github.youndie.kompot.KompotComponent
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The sale's calendar on a stand (B-58): the seed dates the campaigns' and the deals' windows from the day it
 * seeds, so a stand seeded today sells what the canvas sells on Oct 7; and a stand seeded on an earlier day
 * has its sample sale moved to the day it starts on once every sample deal has ended. The sale's promo code,
 * `AUTUMN10`, is dated with the sale wherever the sale is (B-61).
 *
 * Before B-58 the seed wrote the canvas's dates whatever the day: on the stand's wall clock every deal had
 * ended, and with campaigns ending, so would every markdown — the storefront would show no sale at all.
 */
class SaleCalendarTest {
    /**
     * On the canvas's day the seed is the canvas's, row for row — which is what keeps every fixture, body and
     * golden as it was — and on any other day it differs only in the sale's windows, each moved by the same
     * whole number of days: the campaigns', the deals' and the sale's promo code's (B-61), never `SUMMER5`'s.
     */
    @Test
    fun `only the sale's windows move with the seeding day`() {
        val canvas = CatalogSeed.generate(CatalogSeed.CANVAS_DAY)
        assertEquals(SampleCatalog.campaigns, canvas.campaigns, "the canvas's day moved the campaigns")
        assertTrue(canvas.deals.all { it.startsAt == CatalogSeed.DEALS_START && it.endsAt == CatalogSeed.DEALS_END })
        assertTrue(SampleCatalog.SONY_DEAL in canvas.deals)
        assertEquals(SamplePromoCodes.all, canvas.promoCodes, "the canvas's day moved a promo code")

        val stand = CatalogSeed.generate(STAND_DAY)
        assertEquals(
            canvas,
            stand.copy(campaigns = canvas.campaigns, deals = canvas.deals, promoCodes = canvas.promoCodes),
            "more than the windows",
        )
        assertEquals(
            canvas.deals,
            stand.deals.map { it.copy(startsAt = it.startsAt.back(), endsAt = it.endsAt.back()) },
        )
        assertEquals(
            canvas.promoCodes.single { it.code == AUTUMN10 },
            stand.promoCodes.single { it.code == AUTUMN10 }.let {
                it.copy(startsAt = it.startsAt.back(), endsAt = it.endsAt.back())
            },
        )
        assertEquals(
            canvas.promoCodes.single { it.code == SUMMER5 },
            stand.promoCodes.single { it.code == SUMMER5 },
            "a code of no sale moved",
        )
        assertEquals(
            canvas.campaigns,
            stand.campaigns.map {
                it.copy(
                    startsAt = it.startsAt.back(),
                    endsAt = it.endsAt.back(),
                    plusEarlyAccessAt = it.plusEarlyAccessAt?.back(),
                )
            },
        )
    }

    /**
     * The windows move by whole store days and stay on the store's midnights: Jan 14, 2026 is in New York's
     * winter time, −05:00, where Oct 7, 2025 is −04:00 — a shift by a fixed number of hours would open the
     * deals at 23:00 the day before.
     */
    @Test
    fun `a winter day's windows open at the store's midnight`() {
        val stand = CatalogSeed.generate(STAND_DAY)
        val opens = stand.deals.map { it.startsAt.toInstant() }.toSet()
        assertEquals(setOf(STAND_DAY.atStartOfDay(DeliveryCalendar.STORE).toInstant()), opens)
        val sale = stand.campaigns.single { it.slug == SampleCatalog.AUTUMN_MEGA_SALE }
        assertEquals(
            STAND_DAY.plusDays(SALE_DAYS).atStartOfDay(DeliveryCalendar.STORE).toInstant(),
            sale.endsAt.toInstant(),
            "the sale does not end $SALE_DAYS days on at midnight",
        )
        val code = stand.promoCodes.single { it.code == AUTUMN10 }
        assertEquals(sale.startsAt to sale.endsAt, code.startsAt to code.endsAt, "the sale's code has another window")
    }

    /**
     * Every code that names a campaign names one the seed writes: a code tied to a slug no campaign has would be
     * moved with a sale nobody sees.
     */
    @Test
    fun `a sale's code names a seeded campaign`() {
        val slugs = SampleCatalog.campaigns.map { it.slug }
        val named = SamplePromoCodes.all.mapNotNull { it.campaignSlug }
        assertEquals(listOf(SampleCatalog.AUTUMN_MEGA_SALE), named, "the control: AUTUMN10 is the sale's code")
        assertTrue(named.all { it in slugs }, "a code names a campaign the seed does not write: $named")
    }

    /**
     * The bug B-61 fixes: a stand seeded on Jan 14, 2026 opened the Autumn mega sale that day while `AUTUMN10`
     * kept the canvas's window and answered `promo_expired`. Now the code runs exactly as long as the sale: taken
     * that evening and a minute before the sale's last midnight, refused from that midnight on.
     */
    @Test
    fun `a stand seeded on another day takes the sale's code during its sale and refuses it after`() =
        PostgresHarness.freshDatabase().use { database ->
            check(Seeder.seedIfEmpty(Databases.connect(database), CatalogSeed.generate(STAND_DAY)))
            val saleEnds = STAND_DAY.plusDays(SALE_DAYS).atStartOfDay(DeliveryCalendar.STORE)
            haulTest(database, clock = StoreClock { STAND_NOW.toZonedDateTime() }) {
                applyPromo(cartWithHeadphones(), AUTUMN10).assertRefresh()
            }
            haulTest(database, clock = StoreClock { saleEnds.minusMinutes(1) }) {
                applyPromo(cartWithHeadphones(), AUTUMN10).assertRefresh()
            }
            haulTest(database, clock = StoreClock { saleEnds }) {
                applyPromo(cartWithHeadphones(), AUTUMN10)
                    .assertError(HttpStatusCode.UnprocessableEntity, ErrorCode.PromoExpired)
            }
        }

    /**
     * A stand seeded on Jan 14, 2026 and drawn that evening shows the canvas's markdowns live: the six deals at
     * the canvas's prices, struck-through prices and badges, the same count of items on sale, the headphones at
     * $349 under $449, the hero naming its own week and the countdown running to that night's midnight.
     */
    @Test
    fun `a stand seeded today shows the canvas's markdowns`() {
        val canvas = drawn(SeededDatabase.dataSource, CANVAS_NOW)
        val stand =
            PostgresHarness.freshDatabase().use { database ->
                check(Seeder.seedIfEmpty(Databases.connect(database), CatalogSeed.generate(STAND_DAY)))
                drawn(database, StoreClock { STAND_NOW.toZonedDateTime() })
            }
        assertEquals(6, canvas.deals.size, "the control: the canvas draws its six deals")
        assertEquals(canvas.deals, stand.deals, "the stand's deals are not the canvas's")
        assertEquals(canvas.onSale, stand.onSale, "the stand has another count of items on sale")
        assertEquals("$349" to "$449", stand.headphones)
        assertEquals("Autumn mega sale · Jan 14 — 21", stand.hero)
        assertEquals("2026-01-15T00:00-05:00", stand.countdown)
    }

    /**
     * A stand seeded on the canvas's day and started again the day after: every sample deal ended at midnight,
     * so the sample sale moves to that day — exactly the windows a fresh seed of that day writes, `AUTUMN10`'s
     * with it and `SUMMER5`'s left — and the home page draws its six deals again. Started again the same day,
     * nothing moves: the deals are live.
     */
    @Test
    fun `a stand whose sample deals have ended moves its sale to today`() =
        seededFreshDatabase().use { dataSource ->
            val database = Databases.connect(dataSource)
            val canvas = CatalogSeed.generate(CatalogSeed.CANVAS_DAY)
            assertFalse(Seeder.redateSale(database, canvas), "a live sale moved")
            assertEquals(windows(canvas), stored(dataSource))

            val next = CatalogSeed.CANVAS_DAY.plusDays(1)
            assertTrue(Seeder.redateSale(database, CatalogSeed.generate(next)), "an ended sale stayed")
            assertEquals(windows(CatalogSeed.generate(next)), stored(dataSource))
            assertFalse(Seeder.redateSale(database, CatalogSeed.generate(next)), "the sale moved twice in a day")

            val evening = next.atTime(LocalTime.of(19, 47)).atZone(DeliveryCalendar.STORE)
            haulTest(dataSource, clock = StoreClock { evening }) {
                assertEquals(6, deals(tree("/ui/home")).size, "the moved deals are not drawn")
            }
        }

    /**
     * Only the seed's own rows move, and only when every one of them has ended: a deal of the seed still live —
     * the third, ending a day later — keeps the whole sale where it is, its code included; a deal under a sample
     * id on another SKU, a deal of the store's own and a promo code of the store's own for the same sale are not
     * the seed's and never move.
     */
    @Test
    fun `a live sample deal and rows that are not the seed's are left alone`() =
        seededFreshDatabase().use { dataSource ->
            val database = Databases.connect(dataSource)
            val next = CatalogSeed.generate(CatalogSeed.CANVAS_DAY.plusDays(1))
            val canvasStart = CatalogSeed.DEALS_START.toInstant()
            dataSource.sql("UPDATE deals SET ends_at = '${CatalogSeed.DEALS_END.plusDays(1)}' WHERE id = 'deal-3'")
            assertFalse(Seeder.redateSale(database, next), "the sale moved under a live deal")
            assertEquals(canvasStart, dataSource.dealStart("deal-2"), "a sample deal moved")
            assertEquals(canvasStart, dataSource.promoStart(AUTUMN10), "the sale's code moved under a live deal")

            dataSource.sql("UPDATE deals SET ends_at = '${CatalogSeed.DEALS_END}' WHERE id = 'deal-3'")
            dataSource.sql("UPDATE deals SET sku_id = '${SampleCatalog.STONEWARE_MUG}-0' WHERE id = 'deal-4'")
            dataSource.sql(
                "INSERT INTO deals (id, sku_id, price_cents, starts_at, ends_at) VALUES ('own', " +
                    "'${SampleCatalog.SONY_HEADPHONES}-0', 30000, '${CatalogSeed.DEALS_START}', '${CatalogSeed.DEALS_END}')",
            )
            dataSource.sql(
                "INSERT INTO promo_codes (code, percent_off, cap_cents, starts_at, ends_at) VALUES ('OWN10', 10, " +
                    "5000, '${CatalogSeed.DEALS_START}', '${CatalogSeed.DEALS_START.plusDays(SALE_DAYS)}')",
            )
            assertTrue(Seeder.redateSale(database, next), "the control: the rest of the sale has ended")
            assertEquals(
                next.promoCodes
                    .single { it.code == AUTUMN10 }
                    .startsAt
                    .toInstant(),
                dataSource.promoStart(AUTUMN10),
                "the control: the sale's code moved",
            )
            assertEquals(canvasStart, dataSource.promoStart("OWN10"), "the store's own code moved")
            assertEquals(
                next.deals
                    .single { it.id == "deal-2" }
                    .startsAt
                    .toInstant(),
                dataSource.dealStart("deal-2"),
            )
            assertEquals(canvasStart, dataSource.dealStart("deal-4"), "a deal on another SKU moved")
            assertEquals(canvasStart, dataSource.dealStart("own"), "the store's own deal moved")
        }

    /**
     * `AUTUMN10` is the seed's code only while it carries the seed's terms: a store that made it 15 % off made it
     * its own, and its window is the store's too. The rest of the sale moves.
     */
    @Test
    fun `the sale's code under the store's own terms is left alone`() =
        seededFreshDatabase().use { dataSource ->
            dataSource.sql("UPDATE promo_codes SET percent_off = 15 WHERE code = '$AUTUMN10'")
            val next = CatalogSeed.generate(CatalogSeed.CANVAS_DAY.plusDays(1))
            assertTrue(Seeder.redateSale(Databases.connect(dataSource), next), "the control: the sale moved")
            assertEquals(CatalogSeed.DEALS_START.toInstant(), dataSource.promoStart(AUTUMN10), "the store's code moved")
        }

    /** A guest's cart holding the headphones, so a promo code has something to count against. */
    private suspend fun HttpClient.cartWithHeadphones(): String =
        guest().also { putLine(it, "${SampleCatalog.SONY_HEADPHONES}-0", LineChange(quantity = 1)).assertRefresh() }

    /** What the tests compare between the canvas and a stand: prices and windows, never delivery days. */
    private data class Drawn(
        val deals: List<List<String?>>,
        val onSale: String?,
        val headphones: Pair<String, String?>,
        val hero: String,
        val countdown: String?,
    )

    private fun drawn(
        dataSource: DataSource,
        clock: StoreClock,
    ): Drawn {
        var drawn: Drawn? = null
        haulTest(dataSource, clock = clock) {
            val home = tree("/ui/home")
            drawn =
                Drawn(
                    deals = deals(home).map { listOf(it.productId, it.price, it.oldPrice, it.badge) },
                    onSale = tree("/ui/deals").only<PageTitle>().count,
                    headphones =
                        tree("/ui/p/${SampleCatalog.SONY_HEADPHONES}")
                            .only<ProductDetails>()
                            .let { it.price to it.oldPrice },
                    hero = home.only<CampaignHero>().eyebrow,
                    countdown =
                        home
                            .all()
                            .filterIsInstance<SectionHeader>()
                            .single { it.id == "deals-title" }
                            .countdownEndsAt,
                )
        }
        return checkNotNull(drawn)
    }

    private fun deals(home: KompotComponent): List<ProductCard> =
        home
            .all()
            .filterIsInstance<ProductGrid>()
            .singleOrNull { it.id == "deals" }
            ?.cards
            .orEmpty()

    /**
     * [catalog]'s sale windows as instants: each campaign's three, then each deal's two, then each promo code's two
     * (`SUMMER5`'s, which never moves, beside the sale's), in the seed's order.
     */
    private fun windows(catalog: SeedCatalog): List<List<Instant?>> =
        catalog.campaigns.map {
            listOf(
                it.startsAt.toInstant(),
                it.endsAt.toInstant(),
                it.plusEarlyAccessAt?.toInstant(),
            )
        } +
            catalog.deals.map { listOf(it.startsAt.toInstant(), it.endsAt.toInstant()) } +
            catalog.promoCodes.map { listOf(it.startsAt.toInstant(), it.endsAt.toInstant()) }

    /** The same windows as the database holds them, read by the seed's slugs, ids and codes. */
    private fun stored(dataSource: DataSource): List<List<Instant?>> {
        val seed = CatalogSeed.generate(CatalogSeed.CANVAS_DAY)
        return seed.campaigns.map {
            dataSource.instants(
                "SELECT starts_at, ends_at, plus_early_access_at FROM campaigns WHERE slug = '${it.slug}'",
            )
        } + seed.deals.map { dataSource.instants("SELECT starts_at, ends_at FROM deals WHERE id = '${it.id}'") } +
            seed.promoCodes.map {
                dataSource.instants("SELECT starts_at, ends_at FROM promo_codes WHERE code = '${it.code}'")
            }
    }

    private fun DataSource.dealStart(id: String): Instant? =
        instants("SELECT starts_at FROM deals WHERE id = '$id'").single()

    private fun DataSource.promoStart(code: String): Instant? =
        instants("SELECT starts_at FROM promo_codes WHERE code = '$code'").single()

    /** The one row [query] selects, every column a timestamp. */
    private fun DataSource.instants(query: String): List<Instant?> =
        connection.use { c ->
            c.createStatement().use { statement ->
                statement.executeQuery(query).use { rows ->
                    check(rows.next()) { "no row for $query" }
                    (1..rows.metaData.columnCount).map { rows.getObject(it, OffsetDateTime::class.java)?.toInstant() }
                }
            }
        }

    private fun DataSource.sql(statement: String) {
        connection.use { c ->
            c.createStatement().use { it.executeUpdate(statement) }
            c.commit()
        }
    }

    /** A stand's instant back on the canvas's calendar: the same local time, [STAND_DAY]'s distance earlier. */
    private fun OffsetDateTime.back(): OffsetDateTime =
        atZoneSameInstant(DeliveryCalendar.STORE)
            .minusDays(ChronoUnit.DAYS.between(CatalogSeed.CANVAS_DAY, STAND_DAY))
            .toOffsetDateTime()

    private companion object {
        /** A day of New York's winter time, far from the canvas's. */
        val STAND_DAY: LocalDate = LocalDate.of(2026, 1, 14)

        /** That day at the canvas's hour. */
        val STAND_NOW: OffsetDateTime = STAND_DAY.atTime(19, 47, 23).atZone(DeliveryCalendar.STORE).toOffsetDateTime()

        /** The Autumn mega sale's length: Oct 7 to Oct 15. */
        const val SALE_DAYS = 8L

        /** The sale's code (research §6) and the code of no sale. */
        const val AUTUMN10 = "AUTUMN10"
        const val SUMMER5 = "SUMMER5"
    }
}
