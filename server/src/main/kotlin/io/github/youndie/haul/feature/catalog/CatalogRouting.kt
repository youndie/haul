package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.feature.catalog.domain.CatalogError
import io.github.youndie.haul.feature.catalog.domain.Filters
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.feature.catalog.domain.Sort
import io.github.youndie.haul.feature.catalog.screen.CatalogRequest
import io.github.youndie.haul.feature.catalog.screen.CatalogScreen
import io.github.youndie.haul.feature.catalog.screen.DealsScreen
import io.github.youndie.haul.feature.catalog.screen.HomeScreen
import io.github.youndie.haul.feature.catalog.screen.ProductScreen
import io.github.youndie.haul.feature.catalog.screen.ProductTab
import io.github.youndie.haul.feature.recommendations.domain.RecordView
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.shell.Viewers
import io.github.youndie.kompot.ktor.respondKompotComponent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject
import java.math.BigDecimal

/**
 * The browse screens (endpoint-catalog), all in the public tier: a guest sees them as a customer does,
 * save the header, which greets a customer and counts whoever's cart.
 * Every refusal is a [CatalogError], which the application answers with its status and body.
 */
internal fun Route.catalogRouting() {
    val home by inject<HomeScreen>()
    val catalog by inject<CatalogScreen>()
    val product by inject<ProductScreen>()
    val deals by inject<DealsScreen>()
    val photos by inject<ProductPhotos>()
    val viewers by inject<Viewers>()
    val recordView by inject<RecordView>()

    get("/ui/home") { call.respondKompotComponent(haulWireJson, home.build(viewers.of(call))) }

    get("/ui/deals") {
        val page =
            call.request.queryParameters["page"]?.let {
                it.toIntOrNull()?.takeIf { p -> p >= 1 }
                    ?: throw CatalogError.Invalid("page", "Pages start at 1, not «$it»")
            } ?: 1
        call.respondKompotComponent(haulWireJson, deals.build(page, viewers.of(call)))
    }

    get("/ui/c/{path...}") {
        val path =
            call.parameters
                .getAll("path")
                .orEmpty()
                .joinToString("/")
        call.respondKompotComponent(
            haulWireJson,
            catalog.build(catalogRequest(path, call.request.queryParameters), viewers.of(call)),
        )
    }

    // Opening a product page is the view «Picked for you» is made from (B-25): the tree is what a shopper
    // fetches when they open the page, so its GET records it, with no command for the client to send. A tab,
    // a variant or a refresh fetches it again and only moves the same view to now. The write runs beside the
    // page's reads, and a failed one is logged, never answered.
    get("/ui/p/{productId}") {
        val query = call.request.queryParameters
        val tab =
            query["tab"]?.let { ProductTab.of(it) ?: throw CatalogError.Invalid("tab", "No tab «$it»") }
                ?: ProductTab.Description
        val productId = call.parameters["productId"]!!
        val viewer = viewers.of(call)
        val tree =
            coroutineScope {
                launch { recordView(viewer, productId) }
                product.build(productId, query["sku"], tab, viewer)
            }
        call.respondKompotComponent(haulWireJson, tree)
    }

    // A product's photo out of the object storage (B-30), served from the server's own origin so the
    // bucket stays private and the browser needs no CORS. Keys carry the content's hash, so an answer
    // never changes and may be cached for good. No store, a key outside the photos, or nothing there:
    // 404, and the client keeps the placeholder tile.
    get("${ProductPhotos.PATH}/{key...}") {
        val key =
            call.parameters
                .getAll("key")
                .orEmpty()
                .joinToString("/")
        val photo = photos.read(key)
        if (photo == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
        call.response.header("X-Content-Type-Options", "nosniff")
        call.respondBytes(photo.bytes, photoType(photo.contentType))
    }
}

/** The stored media type when it is an image's, anything else as bytes: the route serves photos only. */
private fun photoType(stored: String): ContentType =
    runCatching { ContentType.parse(stored) }
        .getOrNull()
        ?.takeIf { it.match(ContentType.Image.Any) }
        ?: ContentType.Application.OctetStream

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
