package io.github.youndie.haul.shell

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.youndie.haul.StorefrontPage
import io.ktor.http.encodeURLParameter
import io.ktor.http.parseQueryString

// The browser's address is the storefront's navigation state. The addresses are the deeplinks the
// server's `NavigateAction`s carry («/», «/c/headphones?brand=Sony», «/p/p-sony-wh-1000xm6»,
// «/search?q=running%20shoes»); each screen's tree lives at the same address under `/ui`, so the client
// maps one to the other and builds no other URL.

/** Which page an address is, for what the shell draws before its tree arrives and when it does not. */
public enum class PageKind { Home, Catalog, Product, Search, Cart, Other }

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

            StorefrontPage.Checkout,
            StorefrontPage.Account,
            StorefrontPage.SignIn,
            null,
            -> PageKind.Other
        }

    /** Where the server keeps this page's tree: `/` is `/ui/home`, any other address the same under `/ui`. */
    val screen: String = if (path == "/") "/ui/home" + value.removePrefix("/") else "/ui$value"

    /** The search's query, decoded; `null` off the search page or without one. */
    val query: String? get() = if (kind == PageKind.Search) parameter("q") else null

    private fun parameter(name: String): String? =
        value
            .substringAfter('?', "")
            .takeIf { it.isNotEmpty() }
            ?.let { parseQueryString(it)[name] }

    public companion object {
        /** The results for what the shopper typed — the one address the client builds, from its own field. */
        public fun search(query: String): String = "/search?q=" + query.encodeURLParameter()

        /** The suggest panel for what the shopper has typed so far. */
        public fun suggest(query: String): String = "/ui/search/suggest?q=" + query.encodeURLParameter()
    }
}

/** The browser's history as the shell uses it: where the page is, a new entry, and back or forward. */
public interface BrowserHistory {
    /** The address the page is at, its query included. */
    public val location: String

    /** A new entry, so back returns to the one before. */
    public fun push(location: String)

    /** Calls [listener] with the address the browser arrived at on back or forward; returns how to stop. */
    public fun listen(listener: (location: String) -> Unit): () -> Unit
}

/** The address being shown, kept in step with [history] in both directions. */
@Stable
internal class Navigator(
    private val history: BrowserHistory,
) {
    var address: Address by mutableStateOf(Address(history.location))
        private set

    /**
     * A link followed: a new entry, unless it is the page already shown. Only addresses inside the
     * storefront are followed; anything else is not a deeplink this client has a page for.
     */
    fun open(location: String) {
        if (!location.startsWith("/") || location == address.value) return
        history.push(location)
        address = Address(location)
    }

    /** Back or forward: the browser has already moved, the page follows. */
    fun arrived(location: String) {
        address = Address(location)
    }
}
