package io.github.youndie.haul.theme

import androidx.compose.ui.text.platform.Font
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.getFontResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment

/**
 * The app's fonts from the four files' bytes, read before the storefront's first frame (B-150): with Compose's
 * own loading ([rememberHaulFonts]) the first frame was the page in a fallback face, redrawn — and reflowed — in
 * the canvas's faces when the files arrived. `index.html` preloads the same four addresses, so the files cross the
 * link beside the bundle rather than after it, and the reads here take the preloaded answers. `null` when any
 * file could not be read: the caller then draws as before, in the fallback until Compose's loading lands.
 */
@OptIn(ExperimentalResourceApi::class)
internal suspend fun loadHaulFonts(): HaulFonts? {
    val environment = getSystemResourceEnvironment()
    val bytes =
        try {
            coroutineScope {
                HaulFontFile.entries
                    .map { file -> async { file to getFontResourceBytes(environment, file.resource) } }
                    .awaitAll()
                    .toMap()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (failed: Throwable) {
            println("Haul: the fonts could not be read before the first frame, drawing in a fallback: $failed")
            return null
        }
    return haulFonts { Font(it.identity, bytes.getValue(it.file), it.weight, it.style, it.settings) }
}
