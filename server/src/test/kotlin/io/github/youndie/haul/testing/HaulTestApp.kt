package io.github.youndie.haul.testing

import com.zaxxer.hikari.HikariDataSource
import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.catalog.domain.PhotoStore
import io.github.youndie.haul.feature.checkout.domain.TestCardAccess
import io.github.youndie.haul.feature.identity.SignInConfig
import io.github.youndie.haul.haulModule
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.Seeder
import io.github.youndie.haul.ui.CampaignRow
import io.github.youndie.haul.ui.CartBody
import io.github.youndie.haul.ui.CartGroup
import io.github.youndie.haul.ui.CategoryGrid
import io.github.youndie.haul.ui.CheckoutBody
import io.github.youndie.haul.ui.FilteredResults
import io.github.youndie.haul.ui.OrderSummary
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.realtime.KompotScreenResponse
import io.github.youndie.kompot.standard.BoxComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.RowComponent
import io.github.youndie.petich.PetichClock
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.TestApplication
import io.ktor.test.dispatcher.runTestWithRealTime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.withContext
import javax.sql.DataSource

/**
 * One seeded database for every route test: the seed is read-only to the browse routes. It is the stand's
 * seed, the generated reviews and questions included ([Seeder.seedIfEmpty]): seeded once, it is what the
 * product pages, the review tabs and the client's wire bodies are compared against.
 */
internal object SeededDatabase {
    val dataSource: DataSource by lazy {
        PostgresHarness.freshDatabase().also {
            check(
                Seeder.seedIfEmpty(Databases.connect(it), CatalogSeed.generate(CatalogSeed.CANVAS_DAY)),
            )
        }
    }
}

/**
 * The order saga's clock in the tests: the wall clock, as in production. The saga's stamps never reach
 * a tree, and a test that has to move it — the restart that must look like the next minute — passes
 * its own.
 */
@Suppress(
    "ktlint:kapkan:wall-clock",
    "the saga's clock in production is the wall clock; the tests share it rather than inventing one",
)
internal val SAGA_CLOCK: PetichClock = PetichClock { System.currentTimeMillis() }

/** The canvas's «now», so every date in a tree is the one the artboards show. */
internal val CANVAS_NOW: StoreClock = StoreClock { CatalogSeed.NOW.toZonedDateTime() }

/**
 * The application exactly as `main` assembles it, at the canvas's «now» — or at [clock]'s, for a test
 * that has to stand somewhere else in the calendar (B-53's early-access window) — over the shared seeded
 * database — or over [dataSource], for a test that has to change the catalog under the routes — with
 * no object storage unless a test hands it [photoStore] (B-30). Sign-in is off unless [signIn] names a
 * realm (`ShildikHarness.signIn` for a running shildik). The test card ···· 0002 is nobody's unless [testCardKey]
 * opens it, as `HAUL_TEST_CARD_KEY` does on a stand (B-89).
 *
 * **The application is stopped when [block] fails, too** (B-140). Ktor's `testApplication` stops it only after a
 * block that returned: a failed assertion left the application running — its sweeper, its Koin graph — after the
 * test had closed the database under it. The graph is the worse half: the feature modules are shared values whose
 * singles keep what they made until a graph is closed, so the next graph built from them is handed the dead test's
 * repositories, wired to its closed pool, and a test that has nothing wrong fails on «HikariPool-N has been
 * closed» (`PlacementOutageTest` → `PlacementRestartTest` in CI, B-140). Same runner as `testApplication`: real
 * time, its minute, and an uncaught exception in any coroutine still fails the test that is running.
 */
internal fun haulTest(
    dataSource: DataSource = SeededDatabase.dataSource,
    photoStore: PhotoStore? = null,
    signIn: SignInConfig? = null,
    sagaClock: PetichClock = SAGA_CLOCK,
    clock: StoreClock = CANVAS_NOW,
    testCardKey: String? = null,
    block: suspend HttpClient.() -> Unit,
): TestResult =
    runTestWithRealTime {
        val application =
            TestApplication {
                application {
                    haulModule(
                        dataSource,
                        clock,
                        commit = "test",
                        photoStore = photoStore,
                        signIn = signIn,
                        sagaClock = sagaClock,
                        testCard = TestCardAccess(testCardKey),
                    )
                }
            }
        try {
            application.start()
            application.client.block()
        } catch (failure: Throwable) {
            val stopping = runCatching { withContext(NonCancellable) { application.stop() } }
            stopping.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
        application.stop()
    }

/**
 * A database of its own, migrated and seeded: for a test that writes to the catalog. Without the generated
 * reviews and questions unless [generatedReviews] asks for them (B-111): they are most of a fresh seed's time,
 * paid by every test that writes, and only a test that reads a product's reviews needs them — without them a
 * product keeps its counts over the catalogue's own rows, and has no histogram.
 */
internal fun seededFreshDatabase(generatedReviews: Boolean = false): HikariDataSource =
    PostgresHarness.freshDatabase().also {
        check(
            Seeder.seedIfEmpty(
                Databases.connect(it),
                CatalogSeed.generate(CatalogSeed.CANVAS_DAY),
                generatedReviews = generatedReviews,
            ),
        )
    }

internal suspend fun HttpClient.tree(path: String): KompotComponent {
    val response: HttpResponse = get(path)
    check(response.status.value == 200) { "$path answered ${response.status}: ${response.bodyAsText()}" }
    return haulWireJson.decodeKompotComponent(response.bodyAsText())
}

/**
 * The tree of a live screen — the order's (B-29) — out of kompot's `KompotScreenResponse`, which names the
 * channel its updates arrive on; [topic] is that channel, checked so a page that lost it fails here.
 */
internal fun liveTree(
    body: String,
    topic: String,
): KompotComponent {
    val response = haulWireJson.decodeFromString(KompotScreenResponse.serializer(), body)
    check(response.realtimeTopic == topic) { "the page names the channel ${response.realtimeTopic}, not $topic" }
    return response.screen
}

/** Every component in a tree, depth first, through kompot's containers and the Haul components that hold others. */
internal fun KompotComponent.all(): List<KompotComponent> =
    listOf(this) +
        when (this) {
            is ColumnComponent -> children.flatMap { it.all() }
            is RowComponent -> children.flatMap { it.all() }
            is BoxComponent -> children.flatMap { it.all() }
            is CampaignRow -> listOf(hero).flatMap { it.all() } + banners.flatMap { it.all() }
            is CategoryGrid -> tiles.flatMap { it.all() }
            is ProductGrid -> cards
            is FilteredResults -> listOfNotNull(facets, applied, grid, pagination, empty).flatMap { it.all() }
            is CartBody -> listOf(selection) + groups.flatMap { it.all() } + summary.all()
            is CheckoutBody -> notices + listOfNotNull(methods, address, slots, points, payment, summary)
            is CartGroup -> lines
            is OrderSummary -> listOfNotNull(promo)
            else -> emptyList()
        }

internal inline fun <reified T : KompotComponent> KompotComponent.only(): T = all().filterIsInstance<T>().single()
