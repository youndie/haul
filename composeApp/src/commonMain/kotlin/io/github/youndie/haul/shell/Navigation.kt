package io.github.youndie.haul.shell

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.youndie.haul.StorefrontPage
import io.ktor.http.URLDecodeException
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLParameter
import io.ktor.http.parseQueryString

// The browser's address is the storefront's navigation state. The addresses are the deeplinks the
// server's `NavigateAction`s carry («/», «/c/electronics/audio/headphones?brand=Sonora», «/p/p-sonora-sh-718n»,
// «/search?q=running%20shoes»); each screen's tree lives at the same address under `/ui`, so the client
// maps one to the other and builds no other URL. A category has one address, the whole path (B-68): the
// server names no other form and redirects one typed or kept from before, so the paths the shell compares
// (B-62) are the server's own and a filter's address is the page's path.

/** Which page an address is, for what the shell draws before its tree arrives and when it does not. */
public enum class PageKind {
    Home,
    Catalog,
    Product,
    Search,
    Cart,
    Checkout,
    Account,
    Saved,
    Order,

    /** `/c`: every top-level category (B-49). */
    Categories,

    /** `/deals`: today's deals and everything on sale (B-37). */
    Deals,

    /** `/sign-in`: the client's own page, with no tree behind it (B-66). */
    SignIn,

    /** `/about`: «About this demo», what the stand is and is built with (B-160); and `/numbers`, its numbers (B-173). */
    About,

    /** `/live`: «Live orders», every order's saga as it runs (B-164). */
    Live,
    Other,
    ;

    /** A customer's page: the server refuses it to a guest, so a shopper who signs out is not left on it. */
    public val isCustomers: Boolean get() = this == Checkout || this == Account || this == Saved || this == Order
}

/** An address in the storefront: a path and its query, as a `NavigateAction` carries it. */
public data class Address(
    val value: String,
) {
    /** The path without the query; the bare origin is `/`. */
    val path: String = value.substringBefore('?').ifEmpty { "/" }

    /**
     * Read off [StorefrontPage], the list the server serves the page at (B-36), so an address the
     * client draws as a page is one a reload answers too.
     */
    val kind: PageKind =
        when (StorefrontPage.of(path)) {
            StorefrontPage.Home -> PageKind.Home

            StorefrontPage.Catalog -> PageKind.Catalog

            StorefrontPage.Product -> PageKind.Product

            StorefrontPage.Search -> PageKind.Search

            StorefrontPage.Cart -> PageKind.Cart

            StorefrontPage.Checkout -> PageKind.Checkout

            StorefrontPage.Account, StorefrontPage.Orders -> PageKind.Account

            StorefrontPage.Saved -> PageKind.Saved

            StorefrontPage.Order -> PageKind.Order

            StorefrontPage.SignIn -> PageKind.SignIn

            StorefrontPage.Categories -> PageKind.Categories

            StorefrontPage.Deals -> PageKind.Deals

            // «The stack in numbers» (B-173) is drawn as the About page is: the same body in the same frame.
            StorefrontPage.About, StorefrontPage.Numbers -> PageKind.About

            StorefrontPage.Live -> PageKind.Live

            null -> PageKind.Other
        }

    /** Where the server keeps this page's tree: `/` is `/ui/home`, any other address the same under `/ui`. */
    val screen: String = if (path == "/") "/ui/home" + value.removePrefix("/") else "/ui$value"

    /** The search's query, decoded; `null` off the search page, without one, or with one that does not decode. */
    val query: String? get() = if (kind == PageKind.Search) parameter("q") else null

    /**
     * [name]'s first value, decoded — `null` when the query has none or it does not decode. A link cut off
     * mid-escape (`/search?q=%zz`, B-117) is the server's to refuse, and the page says it is out of date; the
     * whole query decoded at once threw instead, from the composition that draws the page's placeholders.
     */
    private fun parameter(name: String): String? =
        parseQueryString(value.substringAfter('?', ""), decode = false)
            .entries()
            .firstOrNull { (written, _) -> written.decodedOrNull(plusIsSpace = false) == name }
            ?.value
            ?.firstOrNull()
            ?.decodedOrNull(plusIsSpace = true)

    private fun String.decodedOrNull(plusIsSpace: Boolean): String? =
        try {
            decodeURLQueryComponent(plusIsSpace = plusIsSpace)
        } catch (_: URLDecodeException) {
            null
        }

    public companion object {
        /**
         * The results for what the shopper typed — the one address the client builds, from its own field — in
         * the [category] the field's picker chose (B-72), all of them when `null`.
         */
        public fun search(
            query: String,
            category: String? = null,
        ): String =
            "/search?q=" + query.encodeURLParameter() + (category?.let { "&category=" + it.encodeURLParameter() } ?: "")

        /**
         * The suggest panel for what the shopper has typed so far, in the [category] the field's picker chose: the
         * panel counts and opens the results there, as the search it leads to does (B-103).
         */
        public fun suggest(
            query: String,
            category: String? = null,
        ): String =
            "/ui/search/suggest?q=" + query.encodeURLParameter() +
                (category?.let { "&category=" + it.encodeURLParameter() } ?: "")
    }
}

/** The browser's history as the shell uses it: where the page is, a new entry, and back or forward. */
public interface BrowserHistory {
    /** The address the page is at, its query included. */
    public val location: String

    /** A new entry, so back returns to the one before. */
    public fun push(location: String)

    /** The entry the page is at says [location] now; back goes where it went before. */
    public fun replace(location: String)

    /** The browser's back: the page hears where it arrived through [listen], as for the shopper's own. */
    public fun back()

    /** Calls [listener] with the address the browser arrived at on back or forward; returns how to stop. */
    public fun listen(listener: (location: String) -> Unit): () -> Unit
}

/**
 * The address being shown, kept in step with [history] in both directions.
 *
 * Two ways to a new address (B-63). A [visit] — a link followed, back or forward — means the page of that
 * address has to be loaded. A [record]ed address is one the page already shows: an `update` replaced the
 * nodes a filter changes and named the address they make, so the history takes the entry and nothing is
 * loaded. [visits] counts the first kind only, which is what the shell loads on.
 */
@Stable
internal class Navigator(
    private val history: BrowserHistory,
) {
    var address: Address by mutableStateOf(Address(history.location))
        private set

    /** How many addresses were visited — opened or arrived at — rather than recorded; each is a page to load. */
    var visits: Int by mutableIntStateOf(0)
        private set

    /**
     * A link followed: a new entry and a visit. The page already shown takes no entry but is visited all the
     * same (B-95) — loaded again in place, as a new address of the screen is (B-62), with what the press
     * opened closed: the cart's «View cart» on the cart, the query already shown submitted again. Only
     * addresses inside the storefront are followed; anything else is not a deeplink this client has a page for.
     */
    fun open(location: String) {
        if (!location.startsWith("/")) return
        val from = address.value
        if (location != from) history.push(location)
        visit(location)
        if (location != from) pushed = from to location
    }

    /** Back or forward: the browser has already moved, the page follows — unless it is a [withdraw]'s back. */
    fun arrived(location: String) {
        val withdrawn = location == withdrawing
        withdrawing = null
        if (withdrawn) address = Address(location) else visit(location)
    }

    /**
     * The address [open] last took an entry for was refused in a way the page still drawn answers: a search too
     * short over the results kept (B-121). The entry is given back — the browser goes back to the address of the
     * page drawn, which is not loaded again — so the address bar, a reload and back all name the page the shopper
     * sees. Anything since — another visit, an address recorded — leaves the history as it is.
     */
    fun withdraw() {
        val (from, to) = pushed ?: return
        if (to != address.value) return
        pushed = null
        withdrawing = from
        address = Address(from)
        history.back()
    }

    /** The page shown, loaded again: what was drawn before a sign-in or a sign-out is not who is looking now. */
    fun reload() {
        visit(address.value)
    }

    /**
     * A page that is only a way to another — `/sign-in` once the shopper is signed in (B-66) — gives its
     * entry to [location], so back goes to the page before it rather than to it again.
     */
    fun redirect(location: String) {
        if (!location.startsWith("/")) return
        pushed = null
        history.replace(location)
        visit(location)
    }

    /**
     * The page drawn is the one at [location] already — an `update` made it so (B-63): a new entry, or the
     * entry it is at when [replace], and no load. An address outside the storefront is not recorded.
     */
    fun record(
        location: String,
        replace: Boolean,
    ) {
        if (!location.startsWith("/") || location == address.value) return
        if (replace) history.replace(location) else history.push(location)
        pushed = null
        address = Address(location)
    }

    /** The entry [open] last took: from which address, to which; `null` once anything else has moved the page. */
    private var pushed: Pair<String, String>? = null

    /** Where a [withdraw]'s back is going: its arrival is the page already drawn, not a visit. */
    private var withdrawing: String? = null

    private fun visit(location: String) {
        pushed = null
        address = Address(location)
        visits += 1
    }
}
