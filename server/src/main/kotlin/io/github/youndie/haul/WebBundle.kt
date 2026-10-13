package io.github.youndie.haul

import io.ktor.http.CacheControl
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.decodeURLPart
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.isHandled
import io.ktor.server.http.content.CompressedFileType
import io.ktor.server.http.content.LocalFileContent
import io.ktor.server.http.content.isCompressionSuppressed
import io.ktor.server.http.content.staticFiles
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.cacheControl
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.util.combineSafe
import java.io.File

/**
 * The browser bundle — the distribution's `web/` — at `/`, the way the stand should send it (B-34).
 *
 * - **Compressed, by the build, not per request.** The image writes a `.br` and a `.gz` beside every
 *   file worth compressing (`docker/Dockerfile`), and the server only picks the one the browser
 *   accepts — brotli first, it is a fifth smaller than gzip on this bundle (research D9). Compressing
 *   8 MB of wasm at brotli's quality 11 on every first visit would be CPU the server does not need to
 *   spend. The `Content-Type` is the original file's, so the module stays `application/wasm` and the
 *   browser still compiles it while it streams. A file with no sibling is sent as it is.
 * - **`Vary: Accept-Encoding` on every answer that has variants**, the uncompressed one included:
 *   Ktor adds it to the compressed answer only, and a cache that stored the plain one without it would
 *   hand it to every browser.
 * - **Cached for a year only where the name is the content.** The two `.wasm` modules are named by a
 *   hash of their bytes (a changed module is a new URL); `index.html`, `composeApp.js` and the fonts keep
 *   their names across releases and are `no-cache`, so a deploy reaches the next visit.
 * - **No source map**, even if a bundle carries one: the distribution already leaves it out
 *   (`server/build.gradle.kts`); this keeps a hand-built `web/` from publishing it.
 * - **The page at the storefront's own addresses** (B-36): `/c/headphones` or `/p/p-001-05` reloaded,
 *   pasted or shared answers `index.html` — the same answer `/` gets, precompressed, `no-cache`, varying
 *   — and the client draws the page, its own not-found included. Only the shapes in [StorefrontPage],
 *   the list the client's navigation reads too; there is still no catch-all fallback (B-27): an unknown
 *   path, `/ui/...` or `/api/...` stays the 404 the client draws, not a 200 page.
 * - **The page whatever its query** (B-117): a storefront address whose query does not decode —
 *   `/search?q=%zz`, `/c/electronics?brand=%E0%A4%A` — is still the page ([PageAtAnyQuery], ahead of the
 *   route); its tree request is what refuses the query, and the client says the link is out of date.
 *
 * Ktor answers [default][io.ktor.server.http.content.StaticContentConfig.default] for every path that
 * is not a file, so the `filter` — which runs before any file is looked for — turns away whatever is neither a file nor a
 * storefront address, and those fall through to the 404.
 */
internal fun Route.webBundle(dir: File) {
    staticFiles("/", dir) {
        preCompressed(*ENCODINGS)
        default(PAGE)
        filter { call -> !dir.answers(call.request.path()) }
        cacheControl { file -> if (file.isContentHashed()) listOf(IMMUTABLE) else listOf(NO_CACHE) }
        modify { file, call ->
            // A compressed answer has suppressed further compression and carries Ktor's own Vary.
            if (!call.isCompressionSuppressed && file.hasCompressedVariant()) {
                call.response.header(HttpHeaders.Vary, HttpHeaders.AcceptEncoding)
            }
        }
    }
}

/** The bundle's page, which every storefront address answers. */
private const val PAGE = "index.html"

/**
 * The page at a storefront address whose query does not decode (B-117): `/search?q=%zz`, `/c/electronics?%zz`,
 * `/p/p-001-05?sku=%zz`, a link cut off mid-escape. Ktor decodes the query as it starts any routed call — the
 * call's parameters are read whole, the query's with them — so the page request itself was refused before
 * [webBundle] saw it: `400` with the API's JSON ([unexpectedFailures]), which the browser showed as the page.
 * A browser's request for a page gets the page: the query is the tree's to refuse, `/ui/…` answering the same
 * query with `400 validation_failed` naming no parameter, which the client draws as «This link is out of date».
 * Decoding leniently instead was rejected: it would open a broken link at a guessed one.
 *
 * So it answers before routing, with [bundle]'s page, and only a `GET` of an address [StorefrontPage] knows whose
 * query does not decode; everything else — a file, `/ui/…`, `/api/…` — is routed as before. Installed after
 * `OneCategoryAddress`, so another form of a category's address still goes to its one address first. The page goes
 * as it is — `no-cache`, as at every address, but not precompressed: a mangled link is not worth a second copy of
 * the static route's choice of encoding, and the page is a few kilobytes.
 *
 * The call ends with the page (B-148). Ktor's routing does not ask whether a call was answered before it resolves
 * one, and resolving reads the parameters: the query that does not decode was thrown after the page had gone, as
 * a `BadRequestException` the catch-all logged and handed to katcher — one group per guest's mangled link on the
 * stand, for an address the store had answered with `200`.
 */
internal val PageAtAnyQuery =
    createApplicationPlugin("PageAtAnyQuery", ::PageAtAnyQueryConfig) {
        val page = pluginConfig.bundle.resolve(PAGE)
        // Not `onCall`: its handler cannot end the call, and routing runs after it whatever was answered.
        application.intercept(ApplicationCallPipeline.Plugins) {
            if (call.isHandled || call.request.httpMethod != HttpMethod.Get) return@intercept
            if (StorefrontPage.of(call.request.path()) == null || call.request.queryDecodes()) return@intercept
            call.response.cacheControl(NO_CACHE)
            call.respond(LocalFileContent(page))
            finish()
        }
    }

internal class PageAtAnyQueryConfig {
    /** The browser bundle's directory, as [webBundle] serves it. */
    lateinit var bundle: File
}

/** Whether Ktor can decode this request's query — the same decoding a route meets when it reads a parameter. */
private fun ApplicationRequest.queryDecodes(): Boolean =
    try {
        queryParameters.isEmpty()
        true
    } catch (_: BadRequestException) {
        false
    }

/**
 * Whether the bundle answers [path] (as the request has it, encoded): a file in it, or the page at a
 * storefront address. A source map is never answered.
 */
private fun File.answers(path: String): Boolean =
    when {
        path.endsWith(".map") -> false

        StorefrontPage.of(path) != null -> true

        // Resolved the way Ktor resolves the file it would send, so the two cannot disagree on «a file».
        else -> runCatching { combineSafe(path.removePrefix("/").decodeURLPart()) }.getOrNull()?.isFile == true
    }

/** In the order the server prefers them; the image writes both (`docker/Dockerfile`). */
private val ENCODINGS = arrayOf(CompressedFileType.BROTLI, CompressedFileType.GZIP)

/** webpack names an emitted asset by its content hash — `bfa5198fb2fe683c613a.wasm` — and nothing else. */
private val CONTENT_HASHED = Regex("[0-9a-f]{16,}\\.wasm")

private fun File.isContentHashed(): Boolean = CONTENT_HASHED.matches(name)

private fun File.hasCompressedVariant(): Boolean = ENCODINGS.any { resolveSibling("$name.${it.extension}").isFile }

/** `public, max-age=31536000, immutable`: Ktor's [CacheControl.MaxAge] has no `immutable`. */
private val IMMUTABLE =
    object : CacheControl(Visibility.Public) {
        override fun toString(): String = "public, max-age=31536000, immutable"
    }

private val NO_CACHE = CacheControl.NoCache(null)
