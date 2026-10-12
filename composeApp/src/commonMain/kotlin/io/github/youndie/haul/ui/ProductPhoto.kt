package io.github.youndie.haul.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import coil3.EventListener
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.serviceLoaderEnabled
import coil3.svg.SvgDecoder
import io.ktor.client.HttpClient

// Product photos (B-30, research D8) and the seed's pictures (B-78). The server sends a photo's address — or,
// without a stored photo, the address of the product's picture — on a card, the product page and every line;
// the client draws it over the placeholder tile, and the tile stays whenever the image is absent, still
// loading or failed to load — an image is decoration, never a hole in the page.

/** Draws a product photo, or [placeholder] until — and unless — the photo is on screen. */
public fun interface PhotoLoader {
    @Composable
    public fun Photo(
        url: String,
        modifier: Modifier,
        placeholder: @Composable () -> Unit,
    )
}

/**
 * The loader the photos are drawn with. The default loads nothing, so a tree drawn without one — a
 * screenshot fixture, a preview — shows the placeholder tiles the canvas draws; the app provides
 * [coilPhotoLoader].
 */
public val LocalPhotoLoader: androidx.compose.runtime.ProvidableCompositionLocal<PhotoLoader> =
    staticCompositionLocalOf { PhotoLoader { _, modifier, placeholder -> Box(modifier) { placeholder() } } }

/** The tag of a photo that is on screen, for the tests that tell a photo from its placeholder. */
public const val PHOTO_TAG: String = "product-photo"

/**
 * The tag of a placeholder tile that is on screen: since the tile says nothing (B-78), what tells the tests it is
 * there.
 */
public const val PLACEHOLDER_TAG: String = "photo-placeholder"

/** [url]'s photo in [modifier]'s box over [placeholder], or the placeholder alone when there is no photo. */
@Composable
internal fun PhotoOrPlaceholder(
    url: String?,
    modifier: Modifier,
    placeholder: @Composable () -> Unit,
) {
    val tagged = @Composable { Box(Modifier.fillMaxSize().testTag(PLACEHOLDER_TAG)) { placeholder() } }
    if (url == null) {
        Box(modifier) { tagged() }
    } else {
        LocalPhotoLoader.current.Photo(url, modifier, tagged)
    }
}

/**
 * A product's tile in a line — a cart line, checkout's «Your order», an order's line, the suggest panel's «Top
 * products» — in [modifier]'s box: the stored photo at [image] over the tile of [tone], as a card draws it (B-108).
 * Without a photo it is the plain tile in [tone], with the corners of [radius].
 */
@Composable
internal fun LineTile(
    image: String?,
    tone: String,
    radius: Dp,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(radius)
    if (image == null) {
        Box(modifier.background(toneColor(tone), shape))
    } else {
        PhotoOrPlaceholder(image, modifier.clip(shape)) { Box(Modifier.fillMaxSize().background(toneColor(tone))) }
    }
}

/**
 * The app's loader: Coil over [http], with a memory cache. The server's addresses are paths on its own
 * origin («/images/products/…»), and Coil takes a path for a file, so a path is resolved against
 * [origin] first. [events] lets a test wait for a load to finish rather than for a guessed time.
 */
public fun coilPhotoLoader(
    http: HttpClient,
    origin: String,
    events: EventListener = EventListener.NONE,
): PhotoLoader {
    val images =
        ImageLoader
            .Builder(PlatformContext.INSTANCE)
            // Off, or Coil also registers the network fetcher it finds on the classpath — over a default
            // `HttpClient()` of its own, ahead of this one — and [http] is never asked.
            .serviceLoaderEnabled(false)
            // The seed's pictures are SVG (B-78): Skia's SVG renderer, which skiko's wasm already carries, draws
            // them at the size the tile asks for.
            .components {
                add(KtorNetworkFetcherFactory(httpClient = { http }))
                add(SvgDecoder.Factory())
            }
            // The browser's HTTP cache keeps the photos — the server marks them immutable — so a disk cache
            // of Coil's would only repeat it; on the desktop target, where the tests run, one kept an
            // answer between runs (a 404 served from /tmp to a test that expected a photo).
            .diskCachePolicy(CachePolicy.DISABLED)
            .eventListener(events)
            .build()
    return CoilPhotoLoader(images) { url -> if (url.startsWith("/")) origin.trimEnd('/') + url else url }
}

private class CoilPhotoLoader(
    private val images: ImageLoader,
    private val resolve: (String) -> String,
) : PhotoLoader {
    @Composable
    override fun Photo(
        url: String,
        modifier: Modifier,
        placeholder: @Composable () -> Unit,
    ) {
        val painter = rememberAsyncImagePainter(resolve(url), images, contentScale = ContentScale.Crop)
        val state by painter.state.collectAsState()
        Box(modifier) {
            // Drawn from the start, because Coil sizes the request by what the painter is drawn into; the
            // painter draws nothing until the photo has arrived, and the placeholder covers it until then.
            Image(
                painter,
                contentDescription = null,
                modifier =
                    Modifier.matchParentSize().then(
                        if (state is AsyncImagePainter.State.Success) Modifier.testTag(PHOTO_TAG) else Modifier,
                    ),
                contentScale = ContentScale.Crop,
            )
            if (state !is AsyncImagePainter.State.Success) placeholder()
        }
    }
}
