package io.github.youndie.haul

/**
 * The pages the storefront has an address for — the paths the server's `NavigateAction`s carry and the
 * browser shows, each page's tree living at the same path under `/ui` (`/` is `/ui/home`).
 *
 * Both halves read this one list: the client to tell which page an address is (`shell/Navigation.kt`),
 * the server to answer a reloaded or shared address with the page instead of a 404 (`WebBundle.kt`,
 * B-36). It is an allow-list on purpose, not «everything that is not a file»: an unknown path, `/ui/...`
 * and `/api/...` stay the 404 they are. A screen added under `/ui` whose address a shopper can land on
 * is added here too, or a reload of it answers 404.
 */
public enum class StorefrontPage {
    /** `/`. */
    Home,

    /** `/c/{path...}`: a category, by the slugs from the root to it (`/c/electronics/audio/headphones`) or its own. */
    Catalog,

    /** `/p/{productId}`. */
    Product,

    /** `/search`, its query in the query string. */
    Search,

    /** `/cart`. */
    Cart,

    /** `/account`, a customer's. */
    Account,

    /** `/sign-in`, where the header sends a guest; the client claims it before it is followed. */
    SignIn,
    ;

    public companion object {
        /** The page [path] is — a path without its query — or `null` when the storefront has none there. */
        public fun of(path: String): StorefrontPage? {
            if (path == "/") return Home
            if (!path.startsWith("/")) return null
            val segments = path.substring(1).split('/')
            // An empty segment — `/c/`, `/p//x`, a trailing slash — is not an address the server writes.
            if (segments.any { it.isEmpty() }) return null
            val rest = segments.size - 1
            return when (segments.first()) {
                "c" -> Catalog.takeIf { rest >= 1 }
                "p" -> Product.takeIf { rest == 1 }
                "search" -> Search.takeIf { rest == 0 }
                "cart" -> Cart.takeIf { rest == 0 }
                "account" -> Account.takeIf { rest == 0 }
                "sign-in" -> SignIn.takeIf { rest == 0 }
                else -> null
            }
        }
    }
}
