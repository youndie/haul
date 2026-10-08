package io.github.youndie.haul

import io.ktor.http.CacheControl
import io.ktor.http.HttpHeaders
import io.ktor.http.decodeURLPart
import io.ktor.server.http.content.CompressedFileType
import io.ktor.server.http.content.isCompressionSuppressed
import io.ktor.server.http.content.staticFiles
import io.ktor.server.request.path
import io.ktor.server.response.header
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
