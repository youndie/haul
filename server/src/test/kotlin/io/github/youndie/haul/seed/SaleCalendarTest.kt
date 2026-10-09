package io.github.youndie.haul.seed

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.PostgresHarness
import io.github.youndie.haul.testing.SeededDatabase
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.CampaignHero
import io.github.youndie.haul.ui.PageTitle
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductDetails
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.SectionHeader
import io.github.youndie.kompot.KompotComponent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The sale's calendar on a stand (B-58): the seed dates the campaigns' and the deals' windows from the day it
 * seeds, so a stand seeded today sells what the canvas sells on Oct 7; and a stand seeded on an earlier day
 * has its sample sale moved to the day it starts on once every sample deal has ended.
 *
 * Before B-58 the seed wrote the canvas's dates whatever the day: on the stand's wall clock every deal had
 * ended, and with campaigns ending, so would every markdown — the storefront would show no sale at all.
 */
class SaleCalendarTest {
    /**
     * On the canvas's day the seed is the canvas's, row for row — which is what keeps every fixture, body and
     * golden as it was — and on any other day it differs only in the sale's windows, each moved by the same
     * whole number of days.
     */
    @Test
    fun `only the sale's windows move with the seeding day`() {
        val canvas = CatalogSeed.generate(CatalogSeed.CANVAS_DAY)
        assertEquals(SampleCatalog.campaigns, canvas.campaigns, "the canvas's day moved the campaigns")
        assertTrue(canvas.deals.all { it.startsAt == CatalogSeed.DEALS_START && it.endsAt == CatalogSeed.DEALS_END })
        assertTrue(SampleCatalog.SONY_DEAL in canvas.deals)

        val stand = CatalogSeed.generate(STAND_DAY)
        assertEquals(canvas, stand.copy(campaigns = canvas.campaigns, deals = canvas.deals), "more than the windows")
        assertEquals(
            canvas.deals,
            stand.deals.map { it.copy(startsAt = it.startsAt.back(), endsAt = it.endsAt.back()) },
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
     * so the sample sale moves to that day — exactly the windows a fresh seed of that day writes — and the
     * home page draws its six deals again. Started again the same day, nothing moves: the deals are live.
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
     * the third, ending a day later — keeps the whole sale where it is; a deal under a sample id on another SKU,
     * and a deal of the store's own, are not the seed's and never move.
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

            dataSource.sql("UPDATE deals SET ends_at = '${CatalogSeed.DEALS_END}' WHERE id = 'deal-3'")
            dataSource.sql("UPDATE deals SET sku_id = '${SampleCatalog.STONEWARE_MUG}-0' WHERE id = 'deal-4'")
            dataSource.sql(
                "INSERT INTO deals (id, sku_id, price_cents, starts_at, ends_at) VALUES ('own', " +
                    "'${SampleCatalog.SONY_HEADPHONES}-0', 30000, '${CatalogSeed.DEALS_START}', '${CatalogSeed.DEALS_END}')",
            )
            assertTrue(Seeder.redateSale(database, next), "the control: the rest of the sale has ended")
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

    /** [catalog]'s sale windows as instants: each campaign's three, then each deal's two, in the seed's order. */
    private fun windows(catalog: SeedCatalog): List<List<Instant?>> =
        catalog.campaigns.map {
            listOf(
                it.startsAt.toInstant(),
                it.endsAt.toInstant(),
                it.plusEarlyAccessAt?.toInstant(),
            )
        } +
            catalog.deals.map { listOf(it.startsAt.toInstant(), it.endsAt.toInstant()) }

    /** The same windows as the database holds them, read by the seed's slugs and ids. */
    private fun stored(dataSource: DataSource): List<List<Instant?>> {
        val seed = CatalogSeed.generate(CatalogSeed.CANVAS_DAY)
        return seed.campaigns.map {
            dataSource.instants(
                "SELECT starts_at, ends_at, plus_early_access_at FROM campaigns WHERE slug = '${it.slug}'",
            )
        } + seed.deals.map { dataSource.instants("SELECT starts_at, ends_at FROM deals WHERE id = '${it.id}'") }
    }

    private fun DataSource.dealStart(id: String): Instant? =
        instants("SELECT starts_at FROM deals WHERE id = '$id'").single()

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
    }
}
