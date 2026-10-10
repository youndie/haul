package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.feature.catalog.domain.CatalogError
import io.github.youndie.haul.feature.catalog.domain.Filters
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.feature.catalog.domain.Sort
import io.github.youndie.haul.feature.catalog.screen.CatalogRequest
import io.github.youndie.haul.feature.catalog.screen.CatalogScreen
import io.github.youndie.haul.feature.catalog.screen.CatalogUrl
import io.github.youndie.haul.feature.catalog.screen.DealsScreen
import io.github.youndie.haul.feature.catalog.screen.HomeScreen
import io.github.youndie.haul.feature.catalog.screen.ProductScreen
import io.github.youndie.haul.feature.catalog.screen.ProductTab
import io.github.youndie.haul.feature.recommendations.domain.RecordView
import io.github.youndie.haul.feature.reviews.screen.ReviewTabs
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.shell.Parts
import io.github.youndie.haul.shell.Viewers
import io.github.youndie.haul.shell.node
import io.github.youndie.haul.shell.respondParts
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
        call.respondKompotComponent(
            haulWireJson,
            deals.build(dealsPage(call.request.queryParameters), viewers.of(call)),
        )
    }

    // A page of the deals loaded in place (B-63, kind `load`): the grid and the pages. A grid from the first
    // page, which has the deals of the day, cannot be partial: the page drawn may not have them, nor they a page.
    get("${Parts.PREFIX}/deals") {
        val page = deals.build(dealsPage(call.request.queryParameters), viewers.of(call))
        call.respondParts(page.takeIf { it.node(DealsScreen.TODAY) == null }, DealsScreen.PARTS)
    }

    // `/ui/c` itself — no category in the path — is the catalog's root (B-49), every top-level category.
    get("/ui/c/{path...}") {
        val path =
            call.parameters
                .getAll("path")
                .orEmpty()
                .joinToString("/")
        if (path.isEmpty()) {
            call.respondKompotComponent(haulWireJson, catalog.root(viewers.of(call)))
            return@get
        }
        call.respondKompotComponent(
            haulWireJson,
            catalog.build(catalogRequest(path, call.request.queryParameters), viewers.of(call)),
        )
    }

    // A filter, a sort or a page of a category loaded in place (B-63, kind `load`): its title, kinds and
    // results. A category that is no longer there cannot be partial: the answer opens the address, whose
    // page says so. The root has no filters, so nothing loads its parts.
    get("${Parts.PREFIX}/c/{path...}") {
        val path =
            call.parameters
                .getAll("path")
                .orEmpty()
                .joinToString("/")
        val request = catalogRequest(path, call.request.queryParameters)
        val page =
            try {
                if (path.isEmpty()) null else catalog.build(request, viewers.of(call))
            } catch (_: CatalogError.CategoryNotFound) {
                null
            }
        call.respondParts(page, CatalogScreen.PARTS, CatalogScreen.OPTIONAL_PARTS)
    }

    // Opening a product page is the view «Picked for you» is made from (B-25): the tree is what a shopper
    // fetches when they open the page, so its GET records it, with no command for the client to send. A tab,
    // a variant or a refresh fetches it again and only moves the same view to now. The write runs beside the
    // page's reads, and a failed one is logged, never answered.
    get("/ui/p/{productId}") {
        val query = call.request.queryParameters
        val tab = productTab(query)
        val productId = call.parameters["productId"]!!
        val viewer = viewers.of(call)
        val tree =
            coroutineScope {
                launch { recordView(viewer, productId) }
                product.build(productId, query["sku"], tab, viewer, productShown(query))
            }
        call.respondKompotComponent(haulWireJson, tree)
    }

    // «Show more reviews» or «Show more questions» loaded in place (B-71, kind `load`): the tab's node. A
    // product that is no longer there cannot be partial: the answer opens the address, whose page says so.
    // Loading the parts is not opening the page, so no view is recorded.
    get("${Parts.PREFIX}/p/{productId}") {
        val query = call.request.queryParameters
        val tab = productTab(query)
        val page =
            try {
                product.build(call.parameters["productId"]!!, query["sku"], tab, viewers.of(call), productShown(query))
            } catch (_: CatalogError.ProductNotFound) {
                null
            }
        call.respondParts(page, listOf(tab.key))
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

/** The deals' pages the grid holds: the page number, `1` when the query names none, from its `from` (B-77). */
private fun dealsPage(query: Parameters): IntRange {
    val page =
        query["page"]?.let {
            it.toIntOrNull()?.takeIf { p -> p >= 1 }
                ?: throw CatalogError.Invalid("page", "Pages start at 1, not «$it»")
        } ?: 1
    return firstPage(query, page)..page
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
    val from = firstPage(query, page)
    val expanded =
        when (val e = query[CatalogUrl.EXPAND]) {
            null -> false
            CatalogUrl.EXPAND_BRANDS -> true
            else -> throw CatalogError.Invalid(CatalogUrl.EXPAND, "Only «brand» expands, not «$e»")
        }
    return CatalogRequest(path, filters, sort, page, expanded, from)
}

/**
 * The first page a grid holds, `from` (B-77): «Show N more» appends the next page to the grid, so an address
 * may draw the pages `from` to [page]. Absent, the grid holds [page] alone; one that is not a page from 1 to
 * [page] is `400 validation_failed`, as a page that is not a number is.
 */
internal fun firstPage(
    query: Parameters,
    page: Int,
): Int =
    query[CatalogUrl.FROM]?.let {
        it.toIntOrNull()?.takeIf { from -> from in 1..page }
            ?: throw CatalogError.Invalid(CatalogUrl.FROM, "The grid starts at a page from 1 to $page, not «$it»")
    } ?: page

private val RATINGS = setOf(BigDecimal("4.5"), BigDecimal("4.0"))

/** The product page's tab, `tab`: the description when absent. */
private fun productTab(query: Parameters): ProductTab =
    query["tab"]?.let { ProductTab.of(it) ?: throw CatalogError.Invalid("tab", "No tab «$it»") }
        ?: ProductTab.Description

/** How many reviews or questions the tab lists, `shown` (B-71): ten when absent. */
private fun productShown(query: Parameters): Int =
    query[ProductScreen.SHOWN]?.let {
        it.toIntOrNull()?.takeIf { n -> n in ReviewTabs.SHOWN..ReviewTabs.MAX_SHOWN }
            ?: throw CatalogError.Invalid(
                ProductScreen.SHOWN,
                "List from ${ReviewTabs.SHOWN} to ${ReviewTabs.MAX_SHOWN}",
            )
    } ?: ReviewTabs.SHOWN
