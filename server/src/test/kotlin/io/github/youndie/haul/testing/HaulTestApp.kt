package io.github.youndie.haul.testing

import com.zaxxer.hikari.HikariDataSource
import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.catalog.domain.PhotoStore
import io.github.youndie.haul.feature.identity.SignInConfig
import io.github.youndie.haul.haulModule
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.Seeder
import io.github.youndie.haul.ui.CampaignRow
import io.github.youndie.haul.ui.CartBody
import io.github.youndie.haul.ui.CartGroup
import io.github.youndie.haul.ui.CategoryGrid
import io.github.youndie.haul.ui.FilteredResults
import io.github.youndie.haul.ui.OrderSummary
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.standard.BoxComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.RowComponent
import io.github.youndie.petich.PetichClock
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import javax.sql.DataSource

/** One seeded database for every route test: the seed is read-only to the browse routes. */
internal object SeededDatabase {
    val dataSource: DataSource by lazy {
        PostgresHarness.freshDatabase().also {
            check(
                Seeder.seedIfEmpty(Databases.connect(it), CatalogSeed.generate()),
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
 * The application exactly as `main` assembles it, at the canvas's «now», over the shared seeded
 * database — or over [dataSource], for a test that has to change the catalog under the routes — with
 * no object storage unless a test hands it [photoStore] (B-30). Sign-in is off unless [signIn] names a
 * realm (`ShildikHarness.signIn` for a running shildik).
 */
internal fun haulTest(
    dataSource: DataSource = SeededDatabase.dataSource,
    photoStore: PhotoStore? = null,
    signIn: SignInConfig? = null,
    sagaClock: PetichClock = SAGA_CLOCK,
    block: suspend HttpClient.() -> Unit,
) = testApplication {
    application {
        haulModule(
            dataSource,
            CANVAS_NOW,
            commit = "test",
            photoStore = photoStore,
            signIn = signIn,
            sagaClock = sagaClock,
        )
    }
    client.block()
}

/** A database of its own, migrated and seeded: for a test that writes to the catalog. */
internal fun seededFreshDatabase(): HikariDataSource =
    PostgresHarness.freshDatabase().also {
        check(Seeder.seedIfEmpty(Databases.connect(it), CatalogSeed.generate()))
    }

internal suspend fun HttpClient.tree(path: String): KompotComponent {
    val response: HttpResponse = get(path)
    check(response.status.value == 200) { "$path answered ${response.status}: ${response.bodyAsText()}" }
    return haulWireJson.decodeKompotComponent(response.bodyAsText())
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
            is FilteredResults -> listOfNotNull(facets, applied, grid, pagination, empty).flatMap { it.all() }
            is CartBody -> listOf(selection) + groups.flatMap { it.all() } + summary.all()
            is CartGroup -> lines
            is OrderSummary -> listOfNotNull(promo)
            else -> emptyList()
        }

internal inline fun <reified T : KompotComponent> KompotComponent.only(): T = all().filterIsInstance<T>().single()
