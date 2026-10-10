package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.shell.Parts
import io.github.youndie.haul.testing.answer
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.json
import io.github.youndie.haul.testing.loads
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.Breadcrumbs
import io.github.youndie.kompot.commands.UpdateAction
import io.github.youndie.kompot.standard.NavigateAction
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * B-68: a category has one address, the slugs from its top-level category down to it. The shell takes two
 * addresses for one screen only when their paths are equal (B-62), so a page opened by a link whose filters
 * named another form of the same category was loaded whole on the first tick — seen on the stand at
 * `/c/electronics/headphones`, whose «Sony» loaded `/ui/parts/c/headphones?brand=Sony` and then the page.
 */
class OneCategoryAddressTest {
    private val seed = CatalogSeed.generate(CatalogSeed.CANVAS_DAY).categories

    /** The address a category has, from the seed rather than from the server: its slugs from the top level. */
    private fun addressOf(slug: String): String {
        val lineage =
            generateSequence(seed.single { it.slug == slug }) { c -> seed.firstOrNull { it.slug == c.parentSlug } }
                .toList()
                .reversed()
        return "/c/" + lineage.joinToString("/") { it.slug }
    }

    /**
     * Every link to a category on every page that draws one — the header, home's tiles, the catalog's root, a
     * category's breadcrumbs, a search's popular tiles and suggestions, a product's breadcrumbs — is the
     * category's address, and its page answers there with no redirect.
     */
    @Test
    fun `every link to a category is its one address`() =
        haulTest {
            val links = PAGES.flatMap { categoryLinks(tree(it).json()) }.toSet()
            // Not only the header's top-level categories: a subcategory and a leaf, from breadcrumbs and suggestions.
            listOf("/c/electronics/audio", "/c/electronics/audio/headphones", "/c/sports/running-gear/running-shoes")
                .forEach { assertTrue(it in links, "no page links $it; the links: $links") }
            val still = config { followRedirects = false }
            links.forEach { link ->
                // A product's brand links to its category with the brand ticked (B-71): the path is the address.
                val path = link.substringBefore('?')
                assertEquals(addressOf(path.substringAfterLast('/')), path, "a link to a category in another form")
                assertEquals(HttpStatusCode.OK, still.get("/ui$link").status, "$link is not where its page is")
            }
        }

    /**
     * On a category page opened by a link, every filter, sort and page loads parts named by the same path, so
     * the shell records the address and loads nothing else — at a leaf, a subcategory and a top-level category.
     */
    @Test
    fun `every load on a category page names the path the page was opened at`() =
        haulTest {
            val still = config { followRedirects = false }
            val crumbs =
                tree("/ui/p/${SampleCatalog.SONY_HEADPHONES}")
                    .only<Breadcrumbs>()
                    .crumbs
                    .mapNotNull { (it.action as? NavigateAction)?.deeplink }
                    .filter { it.startsWith("/c/") }
            assertEquals(listOf("/c/electronics", "/c/electronics/audio", "/c/electronics/audio/headphones"), crumbs)
            (crumbs + "/c/electronics?rating=4.0&expand=brand&sort=price-asc&page=2").forEach { address ->
                val path = address.substringBefore('?')
                val loads = tree("/ui$address").loads()
                assertTrue(loads.size > 5, "$address carries only ${loads.size} loads")
                loads.forEach { url ->
                    assertEquals(Parts.PREFIX + path, url.substringBefore('?'), "a load on $address")
                    val deeplink =
                        when (val action = still.answer(url)) {
                            is UpdateAction -> action.deeplink
                            is NavigateAction -> action.deeplink
                            else -> fail("$url answered $action")
                        }
                    assertEquals(path, deeplink?.substringBefore('?'), "$url: the address the shell records")
                }
            }
        }

    /** The last slug alone, or a path that skips a level, answers `301` to the address, query kept, at all three. */
    @Test
    fun `another form of a category redirects to its address`() =
        haulTest {
            val still = config { followRedirects = false }
            mapOf(
                "/c/headphones?brand=Sony&feature=Noise%20cancelling" to
                    "/c/electronics/audio/headphones?brand=Sony&feature=Noise%20cancelling",
                "/c/electronics/headphones" to "/c/electronics/audio/headphones",
                "/ui/c/headphones?brand=Sony" to "/ui/c/electronics/audio/headphones?brand=Sony",
                "/ui/c/audio/headphones?sort=rating" to "/ui/c/electronics/audio/headphones?sort=rating",
                "/ui/c/audio" to "/ui/c/electronics/audio",
                "${Parts.PREFIX}/c/headphones?brand=Sony" to
                    "${Parts.PREFIX}/c/electronics/audio/headphones?brand=Sony",
            ).forEach { (asked, address) ->
                val response = still.get(asked)
                assertEquals(HttpStatusCode.MovedPermanently, response.status, asked)
                assertEquals(address, response.headers[HttpHeaders.Location], asked)
            }
            // The address itself, the catalog's root and a category that is not there are the routes' own answers.
            mapOf(
                "/ui/c/electronics/audio/headphones?brand=Sony" to HttpStatusCode.OK,
                "/ui/c/electronics" to HttpStatusCode.OK,
                "/ui/c" to HttpStatusCode.OK,
                "/ui/c/no-such-thing" to HttpStatusCode.NotFound,
                "/ui/c/electronics/no-such-thing" to HttpStatusCode.NotFound,
            ).forEach { (asked, status) -> assertEquals(status, still.get(asked).status, asked) }
        }

    private companion object {
        val PAGES =
            listOf(
                "/ui/home",
                "/ui/c",
                "/ui/c/electronics",
                "/ui/c/electronics/audio",
                "/ui/c/electronics/audio/headphones",
                "/ui/search?q=everyday",
                "/ui/search?q=zzqxv",
                "/ui/search/suggest?q=running%20sh",
                "/ui/p/${SampleCatalog.SONY_HEADPHONES}",
                "/ui/deals",
            )

        /** Every `navigate` to a category page in the tree, in order, once each; the catalog's root is not one. */
        fun categoryLinks(tree: JsonElement): List<String> {
            val found = linkedSetOf<String>()

            fun walk(element: JsonElement) {
                when (element) {
                    is JsonObject -> {
                        val deeplink = (element["deeplink"] as? JsonPrimitive)?.contentOrNull
                        if ((element["type"] as? JsonPrimitive)?.contentOrNull == "navigate" && deeplink != null) {
                            if (deeplink.startsWith("/c/")) found += deeplink
                        }
                        element.values.forEach(::walk)
                    }

                    is JsonArray -> {
                        element.forEach(::walk)
                    }

                    else -> {}
                }
            }
            walk(tree)
            return found.toList()
        }
    }
}
