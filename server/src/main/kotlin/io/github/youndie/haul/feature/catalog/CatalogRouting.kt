package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.feature.catalog.domain.CatalogError
import io.github.youndie.haul.feature.catalog.domain.Filters
import io.github.youndie.haul.feature.catalog.domain.Sort
import io.github.youndie.haul.feature.catalog.screen.CatalogRequest
import io.github.youndie.haul.feature.catalog.screen.CatalogScreen
import io.github.youndie.haul.feature.catalog.screen.HomeScreen
import io.github.youndie.haul.feature.catalog.screen.ProductScreen
import io.github.youndie.haul.feature.catalog.screen.ProductTab
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.kompot.ktor.respondKompotComponent
import io.ktor.http.Parameters
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject
import java.math.BigDecimal

/**
 * The browse screens (endpoint-catalog), all in the public tier: a guest sees them as a customer does.
 * Every refusal is a [CatalogError], which the application answers with its status and body.
 */
internal fun Route.catalogRouting() {
    val home by inject<HomeScreen>()
    val catalog by inject<CatalogScreen>()
    val product by inject<ProductScreen>()

    get("/ui/home") { call.respondKompotComponent(haulWireJson, home.build(Viewer())) }

    get("/ui/c/{path...}") {
        val path =
            call.parameters
                .getAll("path")
                .orEmpty()
                .joinToString("/")
        call.respondKompotComponent(
            haulWireJson,
            catalog.build(catalogRequest(path, call.request.queryParameters), Viewer()),
        )
    }

    get("/ui/p/{productId}") {
        val query = call.request.queryParameters
        val tab =
            query["tab"]?.let { ProductTab.of(it) ?: throw CatalogError.Invalid("tab", "No tab «$it»") }
                ?: ProductTab.Description
        call.respondKompotComponent(
            haulWireJson,
            product.build(call.parameters["productId"]!!, query["sku"], tab, Viewer()),
        )
    }
}

/** The category page's query, refused field by field (`validation_failed`) rather than ignored. */
internal fun catalogRequest(
    path: String,
    query: Parameters,
): CatalogRequest {
    fun int(name: String): Int? =
        query[name]?.let {
            it.toIntOrNull()?.takeIf { v -> v >= 0 }
                ?: throw CatalogError.Invalid(name, "«$it» is not a whole number")
        }
    val rating =
        query["rating"]?.let {
            it.toBigDecimalOrNull()?.takeIf { v -> v in RATINGS }
                ?: throw CatalogError.Invalid("rating", "Rating is 4.5 or 4.0, not «$it»")
        }
    val delivery =
        when (val d = query["delivery"]) {
            null -> false
            "tomorrow" -> true
            else -> throw CatalogError.Invalid("delivery", "Delivery is «tomorrow», not «$d»")
        }
    val filters =
        Filters(
            brands = query.getAll("brand").orEmpty().toSet(),
            priceMinDollars = int("price_min"),
            priceMaxDollars = int("price_max"),
            features = query.getAll("feature").orEmpty().toSet(),
            colours = query.getAll("colour").orEmpty().toSet(),
            kind = query["kind"],
            deliveryTomorrow = delivery,
            ratingAtLeast = rating,
        )
    val sort = query["sort"]?.let { Sort.of(it) ?: throw CatalogError.Invalid("sort", "No sort «$it»") } ?: Sort.Popular
    val page = int("page")?.also { if (it < 1) throw CatalogError.Invalid("page", "Pages start at 1") } ?: 1
    return CatalogRequest(path, filters, sort, page)
}

private val RATINGS = setOf(BigDecimal("4.5"), BigDecimal("4.0"))
