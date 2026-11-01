package io.github.youndie.haul.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import coil3.EventListener
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.compose.useExistingImageAsPlaceholder
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.serviceLoaderEnabled
import coil3.size.Size
import io.github.youndie.haul.PictureVersions
import io.github.youndie.haul.theme.HaulColors
import io.ktor.client.HttpClient

// Product photos (B-30, research D8) and the seed's pictures (B-78). The server sends a photo's address — or,
// without a stored photo, the address of the product's picture — on a card, the product page and every line;
// the client draws it over the placeholder tile, and the tile stays whenever the image is absent, still
// loading or failed to load — an image is decoration, never a hole in the page. An SVG is drawn as a vector
// (`VectorPicture.kt`, B-181), and on a dark page as its dark version, which the server serves beside it.

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

/**
 * [url]'s photo in [modifier]'s box over [placeholder], or the placeholder alone when there is no photo.
 *
 * On a dark page (B-181) an SVG is asked for at its dark version's address ([PictureVersions]) — the seed's pictures
 * drawn over their tones' dark counterparts — so the address the loader caches by names the scheme. A photo with no
 * dark version — a seller's raster upload — is matted instead: drawn a little inside its tile, which the dark page
 * draws dark, with rounded corners and a little of the tile showing through, so a bright photograph sits in the page
 * rather than glaring out of it.
 */
@Composable
internal fun PhotoOrPlaceholder(
    url: String?,
    modifier: Modifier,
    placeholder: @Composable () -> Unit,
) {
    val tagged = @Composable { Box(Modifier.fillMaxSize().testTag(PLACEHOLDER_TAG)) { placeholder() } }
    val loader = LocalPhotoLoader.current
    when {
        url == null -> {
            Box(modifier) { tagged() }
        }

        !HaulColors.scheme.isDark -> {
            loader.Photo(url, modifier, tagged)
        }

        else -> {
            val dark = PictureVersions.dark(url)
            if (dark != null) {
                loader.Photo(dark, modifier, tagged)
            } else {
                Box(modifier, contentAlignment = Alignment.Center) {
                    Box(Modifier.matchParentSize()) { placeholder() }
                    loader.Photo(
                        url,
                        Modifier
                            .fillMaxSize(MATTED)
                            .clip(RoundedCornerShape(MAT_CORNER_PERCENT))
                            .graphicsLayer { alpha = MATTED_ALPHA },
                        tagged,
                    )
                }
            }
        }
    }
}

/** How much of its tile a matted photo covers, how round its corners are, and how much of the tile shows through. */
private const val MATTED = 0.88f
private const val MAT_CORNER_PERCENT = 6
private const val MATTED_ALPHA = 0.9f

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
            // The seed's pictures are SVG (B-78): Skia's SVG renderer, which skiko's wasm already carries, parses each
            // once and records it, and the page replays it as vectors at whatever size it is drawn (B-181).
            .components {
                add(KtorNetworkFetcherFactory(httpClient = { http }))
                add(VectorPictureDecoder.Factory())
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
    ) = key(url) {
        // An SVG is a vector (B-181): one decode serves every size, so it is asked for at once, at its own size.
        if (url.endsWith(SVG)) {
            Box(modifier) { Sized(resolve(url), null, placeholder) }
        } else {
            Raster(url, modifier, placeholder)
        }
    }

    /**
     * A raster photo, decoded at the size it is drawn and again whenever its box outgrows that (B-149): a request
     * that names no size is decoded at the photo's own, which a large box scaled up. Never asked for smaller: a
     * larger bitmap drawn smaller stays sharp. Asked for once the box has a size.
     */
    @Composable
    private fun Raster(
        url: String,
        modifier: Modifier,
        placeholder: @Composable () -> Unit,
    ) {
        var decodeAt by remember { mutableStateOf<IntSize?>(null) }
        Box(modifier.onSizeChanged { box -> decodeAt = outgrown(decodeAt, box) }) {
            val size = decodeAt
            if (size == null) placeholder() else Sized(resolve(url), size, placeholder)
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    @Composable
    private fun BoxScope.Sized(
        url: String,
        size: IntSize?,
        placeholder: @Composable () -> Unit,
    ) {
        val context = LocalPlatformContext.current
        val request =
            remember(size) {
                ImageRequest
                    .Builder(context)
                    .data(url)
                    .size(size?.let { Size(it.width, it.height) } ?: Size.ORIGINAL)
                    // Asked again at a larger size, the picture already on screen stays until the sharper one is in.
                    .useExistingImageAsPlaceholder(true)
                    .build()
            }
        val painter = rememberAsyncImagePainter(request, images, contentScale = ContentScale.Crop)
        val state by painter.state.collectAsState()
        // On a page come back to (B-168) a photo is drawn as it arrives, with no fade: the page arrives at rest.
        val arrivesAtRest = LocalArrivesAtRest.current
        val fades = remember { !arrivesAtRest }
        if (lively() && fades) {
            Arriving(painter, state, placeholder)
            return
        }
        // The painter draws nothing until the photo has arrived, and the placeholder covers it until then.
        Image(
            painter,
            contentDescription = null,
            modifier =
                Modifier.matchParentSize().then(
                    if (state is AsyncImagePainter.State.Success) Modifier.testTag(PHOTO_TAG) else Modifier,
                ),
            contentScale = ContentScale.Crop,
        )
        if (state !is AsyncImagePainter.State.Success && state.painter == null) placeholder()
    }

    /**
     * On a lively page a photo that arrives fades in over its placeholder and settles from a little nearer (B-158);
     * one already there when it is first drawn — from the memory cache — is drawn at once.
     */
    @Composable
    private fun BoxScope.Arriving(
        painter: AsyncImagePainter,
        state: AsyncImagePainter.State,
        placeholder: @Composable () -> Unit,
    ) {
        val arrived = state is AsyncImagePainter.State.Success
        val shown = remember { Animatable(if (arrived) 1f else 0f) }
        LaunchedEffect(arrived) { if (arrived) shown.animateTo(1f, tween(PHOTO_FADE_MS, easing = Arrive)) }
        if (shown.value < 1f && state.painter == null) placeholder()
        Image(
            painter,
            contentDescription = null,
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val at = shown.value
                        alpha = at
                        scaleX = 1f + PHOTO_SETTLE * (1f - at)
                        scaleY = 1f + PHOTO_SETTLE * (1f - at)
                    }.then(if (arrived) Modifier.testTag(PHOTO_TAG) else Modifier),
            contentScale = ContentScale.Crop,
        )
    }
}

/** How long an arriving photo takes to fade in, and how much nearer it starts. */
private const val PHOTO_FADE_MS = 480
private const val PHOTO_SETTLE = 0.04f

/**
 * The size a photo drawn in [box] is decoded at: [decoded] while it covers the box, else the box rounded up to
 * [DECODE_STEP] pixels — so a page widened by a drag asks again every few steps, not at every pixel.
 */
private fun outgrown(
    decoded: IntSize?,
    box: IntSize,
): IntSize? {
    if (box.width <= 0 || box.height <= 0) return decoded
    if (decoded != null && box.width <= decoded.width && box.height <= decoded.height) return decoded

    fun up(px: Int) = (px + DECODE_STEP - 1) / DECODE_STEP * DECODE_STEP
    return IntSize(maxOf(up(box.width), decoded?.width ?: 0), maxOf(up(box.height), decoded?.height ?: 0))
}

private const val DECODE_STEP = 64

private const val SVG = ".svg"
