package io.github.youndie.haul

import io.ktor.http.CacheControl
import io.ktor.http.HttpHeaders
import io.ktor.server.http.content.CompressedFileType
import io.ktor.server.http.content.isCompressionSuppressed
import io.ktor.server.http.content.staticFiles
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.routing.Route
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
 *
 * And still no fallback to `index.html` for a missing file (B-27): an unknown `/ui/...` stays the 404
 * the client draws.
 */
internal fun Route.webBundle(dir: File) {
    staticFiles("/", dir) {
        preCompressed(*ENCODINGS)
        filter { call -> call.request.path().endsWith(".map") }
        cacheControl { file -> if (file.isContentHashed()) listOf(IMMUTABLE) else listOf(NO_CACHE) }
        modify { file, call ->
            // A compressed answer has suppressed further compression and carries Ktor's own Vary.
            if (!call.isCompressionSuppressed && file.hasCompressedVariant()) {
                call.response.header(HttpHeaders.Vary, HttpHeaders.AcceptEncoding)
            }
        }
    }
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
