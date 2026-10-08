package io.github.youndie.haul.testing

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.haulModule
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.Seeder
import io.github.youndie.haul.ui.CampaignRow
import io.github.youndie.haul.ui.CategoryGrid
import io.github.youndie.haul.ui.FilteredResults
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.standard.BoxComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.RowComponent
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

/** The canvas's «now», so every date in a tree is the one the artboards show. */
internal val CANVAS_NOW: StoreClock = StoreClock { CatalogSeed.NOW.toZonedDateTime() }

/** The application exactly as `main` assembles it, over the seeded database, at the canvas's «now». */
internal fun haulTest(block: suspend HttpClient.() -> Unit) =
    testApplication {
        application { haulModule(SeededDatabase.dataSource, CANVAS_NOW, commit = "test") }
        client.block()
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
            else -> emptyList()
        }

internal inline fun <reified T : KompotComponent> KompotComponent.only(): T = all().filterIsInstance<T>().single()
