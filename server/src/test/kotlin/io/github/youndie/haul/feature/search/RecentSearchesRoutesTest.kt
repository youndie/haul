package io.github.youndie.haul.feature.search

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.testing.ShildikHarness
import io.github.youndie.haul.testing.assertError
import io.github.youndie.haul.testing.assertRefresh
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.PageTitle
import io.github.youndie.haul.ui.SearchSuggestPanel
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * «Clear» on recent searches (B-37), over HTTP against a running shildik: the suggest panel of a
 * customer with recent searches carries the URL to clear them, `DELETE` there empties them, and the
 * route is the customer tier's.
 */
class RecentSearchesRoutesTest {
    @Test
    fun `clear empties a customer's recent searches and the panel no longer offers it`() {
        val token = ShildikHarness.accessToken(ShildikHarness.person("Rita Recent"))
        haulTest(signIn = ShildikHarness.signIn) {
            assertEquals(HttpStatusCode.OK, get("/ui/search?q=mugs") { bearerAuth(token) }.status)
            val before = suggest(token)
            assertEquals(
                listOf("mugs"),
                before.recent.map { it.label },
                "the search was not recorded — nothing to clear",
            )
            assertEquals("/api/v1/me/recent-searches", before.clearUrl)

            delete(before.clearUrl!!) { bearerAuth(token) }.assertRefresh()
            val after = suggest(token)
            assertEquals(emptyList(), after.recent)
            assertNull(after.clearUrl, "«Clear» offered over nothing to clear")
        }
    }

    /**
     * B-49: a recent search's row carried nothing, and the client would have had to build `/search?q=`
     * from the text. The tree now carries that search's address, the query encoded, and it answers the
     * results for the query as typed.
     */
    @Test
    fun `a recent search's row runs that search again`() {
        val token = ShildikHarness.accessToken(ShildikHarness.person("Rhea Rerun"))
        haulTest(signIn = ShildikHarness.signIn) {
            assertEquals(HttpStatusCode.OK, get("/ui/search?q=stoneware%20mug") { bearerAuth(token) }.status)
            val row = suggest(token).recent.single()
            assertEquals("stoneware mug", row.label)
            assertEquals(NavigateAction("/search?q=stoneware%20mug"), row.action)

            val deeplink = (row.action as NavigateAction).deeplink
            val results = get("/ui$deeplink") { bearerAuth(token) }
            assertEquals(HttpStatusCode.OK, results.status, results.bodyAsText())
            val title = haulWireJson.decodeKompotComponent(results.bodyAsText()).only<PageTitle>()
            assertEquals("Stoneware mug", title.title, "not the results for the recent query")
        }
    }

    @Test
    fun `clearing needs a sign-in and a guest is offered nothing to clear`() =
        haulTest(signIn = ShildikHarness.signIn) {
            delete("/api/v1/me/recent-searches").assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
            assertNull((tree("/ui/search/suggest?q=mugs") as SearchSuggestPanel).clearUrl)
        }

    /**
     * B-124: the empty field pressed asked for nothing, and the recent searches showed only once two letters were
     * typed. Suggest with an empty `q` is that press: the customer's last searches alone, newest first, a repeat
     * on top, each row in the picker's scope, with «Clear» — and nothing searched for an empty query.
     */
    @Test
    fun `the empty field offers a customer's recent searches alone, newest first, in the scope`() {
        val token = ShildikHarness.accessToken(ShildikHarness.person("Maya Recall"))
        // Each search a second after the one before, so «newest first» is the clock's order, not the table's.
        val tick = AtomicLong()
        val clock = StoreClock { CatalogSeed.NOW.toZonedDateTime().plusSeconds(tick.incrementAndGet()) }
        haulTest(signIn = ShildikHarness.signIn, clock = clock) {
            listOf("mug", "sony", "duvet", "mug").forEach {
                assertEquals(HttpStatusCode.OK, get("/ui/search?q=$it") { bearerAuth(token) }.status, it)
            }

            val panel = suggest(token, "")
            assertEquals(listOf("mug", "duvet", "sony"), panel.recent.map { it.label })
            assertEquals(NavigateAction("/search?q=duvet"), panel.recent[1].action)
            assertEquals("/api/v1/me/recent-searches", panel.clearUrl)
            assertEquals("", panel.query)
            assertEquals(emptyList(), panel.suggestions, "an empty query was suggested for")
            assertEquals(emptyList(), panel.categories)
            assertEquals(emptyList(), panel.products)
            assertNull(panel.allResultsAction, "«All N results» offered for nothing typed")

            val scoped = suggest(token, "&category=home-kitchen")
            assertEquals(
                listOf(
                    "/search?q=mug&category=home-kitchen",
                    "/search?q=duvet&category=home-kitchen",
                    "/search?q=sony&category=home-kitchen",
                ),
                scoped.recent.map { (it.action as NavigateAction).deeplink },
                "a recent row left the picker's scope",
            )
            assertEquals(
                HttpStatusCode.NotFound,
                get("/ui/search/suggest?q=&category=nowhere") { bearerAuth(token) }.status,
            )
        }
    }

    /** B-124: a guest's empty field — the client asks nothing, and the route, asked, has nothing to offer. */
    @Test
    fun `the empty field offers a guest nothing and one character is still too short`() =
        haulTest(signIn = ShildikHarness.signIn) {
            val panel = tree("/ui/search/suggest?q=") as SearchSuggestPanel
            assertEquals(emptyList(), panel.recent)
            assertNull(panel.clearUrl)
            assertEquals(emptyList(), panel.products)
            get("/ui/search/suggest?q=m").assertError(HttpStatusCode.BadRequest, ErrorCode.QueryTooShort)
        }

    private suspend fun HttpClient.suggest(
        token: String,
        query: String = "mugs",
    ): SearchSuggestPanel {
        val response = get("/ui/search/suggest?q=$query") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return haulWireJson.decodeKompotComponent(response.bodyAsText()) as SearchSuggestPanel
    }
}
