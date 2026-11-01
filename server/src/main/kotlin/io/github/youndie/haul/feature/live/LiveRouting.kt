package io.github.youndie.haul.feature.live

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.screen.navigation
import io.github.youndie.haul.feature.identity.Callers
import io.github.youndie.haul.feature.live.domain.FeedRepository
import io.github.youndie.haul.feature.live.screen.LiveScreen
import io.github.youndie.haul.feature.order.LiveOrders
import io.github.youndie.haul.feature.order.respondOrderUpdates
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.respondError
import io.github.youndie.haul.shell.UPDATES
import io.github.youndie.haul.shell.Viewers
import io.github.youndie.haul.shell.respondUpdates
import io.github.youndie.kompot.realtime.KompotScreenResponse
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject

/**
 * «Live orders» (B-164), in the public tier: the page's tree (`GET /ui/live`, the page at `/live`, a `StorefrontPage`
 * so a reload of it is the page) and the stream every live page listens on, `GET /ui/updates`.
 *
 * The page's tree comes in kompot's `KompotScreenResponse`, naming [LiveFeed.TOPIC]; the stream of that topic is
 * anybody's, and is refused `503 unavailable` once [LiveFeed.CAPACITY] listen. Any other topic is an order's page's
 * (B-29), answered as the customer tier answers it ([respondOrderUpdates]): the route is here, and not under the
 * customer tier, because one address serves both and a topic is what decides who may hear it.
 */
internal fun Route.liveRouting() {
    val viewers by inject<Viewers>()
    val categories by inject<CatalogRepository>()
    val feed by inject<FeedRepository>()
    val screen by inject<LiveScreen>()
    val live by inject<LiveFeed>()
    val callers by inject<Callers>()
    val orders by inject<LiveOrders>()

    get(LIVE_SCREEN) {
        val tree = screen.page(viewers.of(call), navigation(categories.categories()), feed.latest(LiveScreen.LIMIT))
        call.respondText(
            haulWireJson.encodeToString(
                KompotScreenResponse.serializer(),
                KompotScreenResponse(tree, realtimeTopic = LiveFeed.TOPIC),
            ),
            ContentType.Application.Json,
        )
    }

    get(UPDATES) {
        val topic = call.request.queryParameters["topic"].orEmpty()
        if (topic != LiveFeed.TOPIC) return@get call.respondOrderUpdates(topic, callers, orders)
        if (!live.admit()) {
            return@get call.respondError(
                ErrorCode.Unavailable,
                "Too many are watching the live orders: try again shortly",
            )
        }
        try {
            call.respondUpdates(subscribe = live::subscribe, unsubscribe = live::unsubscribe, first = live::frame)
        } finally {
            live.leave()
        }
    }
}

/** The page's tree: the page is at [LiveScreen.ADDRESS], `/live`. */
internal const val LIVE_SCREEN = "/ui/live"
