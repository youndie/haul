package io.github.youndie.haul

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import coil3.BitmapImage
import coil3.EventListener
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import io.github.youndie.haul.theme.HaulColorScheme
import io.github.youndie.haul.ui.LocalPhotoLoader
import io.github.youndie.haul.ui.PHOTO_TAG
import io.github.youndie.haul.ui.PLACEHOLDER_TAG
import io.github.youndie.haul.ui.PhotoLoader
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductCardView
import io.github.youndie.haul.ui.coilPhotoLoader
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-30's fallback, through the app's own loader (Coil over a mock HTTP engine) and the card's renderer:
 * the placeholder tile stays whenever the photo is not on screen — absent, still loading, or failed —
 * and gives way only to a photo that arrived. A photo that fails and leaves a blank square, or a
 * placeholder drawn over a photo that loaded, is what these catch.
 */
@OptIn(ExperimentalTestApi::class)
class PhotoFallbackTest {
    private val card =
        ProductCard(
            id = "card-1",
            productId = "p-sonora-sh-718n",
            title = "Sonora SH-718N",
            price = "$349",
            rating = "4.8",
            reviews = "2,341",
            delivery = "Tomorrow",
            tone = "#E6E4FF",
            image = "/images/products/p-sonora-sh-718n/1.png",
        )
    private val requests = CopyOnWriteArrayList<String>()
    private val finished = CountDownLatch(1)
    private val events =
        object : EventListener() {
            override fun onSuccess(
                request: ImageRequest,
                result: SuccessResult,
            ) = finished.countDown()

            override fun onError(
                request: ImageRequest,
                result: ErrorResult,
            ) = finished.countDown()
        }

    private fun loader(answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): PhotoLoader =
        coilPhotoLoader(
            HttpClient(
                MockEngine {
                    requests += it.url.toString()
                    answer(it)
                },
            ),
            origin = "http://haul.test",
            events = events,
        )

    private fun ComposeUiTest.drawCard(
        shown: ProductCard,
        photos: PhotoLoader,
        scheme: HaulColorScheme = HaulColorScheme.Light,
    ) = setContent {
        CompositionLocalProvider(LocalFixtureScheme provides scheme) {
            Fixture(compact = false) {
                CompositionLocalProvider(LocalPhotoLoader provides photos) {
                    Box(Modifier.width(236.dp)) { ProductCardView(shown) }
                }
            }
        }
    }

    /**
     * Until Coil reports the load finished, either way. Waited for through the test's own clock, not on
     * the latch: the load completes on the composition's dispatcher, which only runs while the test
     * pumps it, so blocking the test thread on the latch would wait for itself.
     */
    private fun ComposeUiTest.awaitLoad() =
        waitUntil("the photo finished loading", timeoutMillis = 10_000) {
            finished.count ==
                0L
        }

    @Test
    fun `a photo that arrived replaces the placeholder tile`() =
        runComposeUiTest {
            drawCard(
                card,
                loader { respond(png(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/png")) },
            )
            awaitLoad()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTagCount(PHOTO_TAG) == 1 }
            onNodeWithTag(PLACEHOLDER_TAG).assertDoesNotExist()
            // The server's path, resolved against the page's origin: Coil takes a bare path for a file.
            assertEquals(listOf("http://haul.test/images/products/p-sonora-sh-718n/1.png"), requests)
        }

    @Test
    fun `a picture sent as SVG replaces the placeholder tile`() =
        runComposeUiTest {
            // The seed's pictures (B-78) are SVG: Coil must decode them, or the tile would stay for good.
            val picture = "/images/art/p_headphones_1_silver_e6e4ff.0123456789.svg"
            drawCard(
                card.copy(image = picture),
                loader { respond(SVG, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/svg+xml")) },
            )
            awaitLoad()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTagCount(PHOTO_TAG) == 1 }
            onNodeWithTag(PLACEHOLDER_TAG).assertDoesNotExist()
            assertEquals(listOf("http://haul.test$picture"), requests)
        }

    /**
     * B-149: a raster photo is decoded at the size it is asked for. Asked for with no size, it was decoded at its own
     * and drawn scaled up — soft in a large box. It is decoded at its box's size, again once the box outgrows that,
     * and never for a smaller one.
     */
    @Test
    fun `a raster photo whose box grows is decoded again at its new size`() =
        runComposeUiTest {
            val widths = CopyOnWriteArrayList<Int>()
            val photos = recording(widths, png(), "image/png")
            var side by mutableStateOf(60.dp)
            var density = 1f
            setContent {
                density = LocalDensity.current.density
                Box(Modifier.size(side)) { photos.Photo("/images/products/p/1.png", Modifier.fillMaxSize()) {} }
            }
            waitUntil("the photo decoded", timeoutMillis = 10_000) { widths.isNotEmpty() }
            assertTrue(widths.single() >= 60 * density, "decoded at ${widths.single()} px for a 60 dp box")

            side = 300.dp
            waitUntil("the photo decoded again", timeoutMillis = 10_000) { widths.size == 2 }
            assertTrue(widths.last() >= 300 * density, "decoded at ${widths.last()} px for a 300 dp box")
            onNodeWithTag(PHOTO_TAG).assertExists()

            side = 120.dp
            waitForIdle()
            assertEquals(2, widths.size, "a smaller box asked again: $widths")
        }

    /**
     * B-181: a picture is SVG and is drawn as a vector — recorded once, not rasterised — so it is decoded once, at
     * its own size, and a box that grows asks for nothing: the same recording is replayed at the new size.
     */
    @Test
    fun `a picture is decoded once as a vector, whatever its box's size`() =
        runComposeUiTest {
            val widths = CopyOnWriteArrayList<Int>()
            val bitmaps = CopyOnWriteArrayList<Boolean>()
            val photos = recording(widths, SVG, "image/svg+xml", bitmaps)
            var side by mutableStateOf(60.dp)
            setContent {
                Box(Modifier.size(side)) { photos.Photo("/images/art/p.svg", Modifier.fillMaxSize()) {} }
            }
            waitUntil("the picture decoded", timeoutMillis = 10_000) { widths.isNotEmpty() }
            assertEquals(listOf(24), widths, "decoded at its own size")
            assertEquals(listOf(false), bitmaps, "decoded to a bitmap")
            onNodeWithTag(PHOTO_TAG).assertExists()

            side = 300.dp
            waitForIdle()
            assertEquals(listOf(24), widths, "a larger box asked again")
        }

    /**
     * B-181: on a dark page a picture is asked for at its dark version's address, which the server serves beside it;
     * a photo with no dark version — a raster upload — is asked for as it is, and drawn matted inside its tile.
     */
    @Test
    fun `on a dark page a picture is its dark version and a raster photo is matted`() =
        runComposeUiTest {
            val picture = "/images/art/p_headphones_1_silver_e6e4ff.0123456789.svg"
            drawCard(
                card.copy(image = picture),
                loader { respond(SVG, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/svg+xml")) },
                HaulColorScheme.Dark,
            )
            awaitLoad()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTagCount(PHOTO_TAG) == 1 }
            assertEquals(listOf("http://haul.test${PictureVersions.dark(picture)}"), requests)
            val tile = onNodeWithTag(PHOTO_TAG).fetchSemanticsNode().boundsInRoot
            assertEquals(236f, tile.width / density.density, 1f, "the picture fills its tile")
        }

    @Test
    fun `on a dark page a raster photo is asked for as it is and drawn inside its tile`() =
        runComposeUiTest {
            drawCard(
                card,
                loader { respond(png(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/png")) },
                HaulColorScheme.Dark,
            )
            awaitLoad()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTagCount(PHOTO_TAG) == 1 }
            assertEquals(listOf("http://haul.test${card.image}"), requests)
            val photo = onNodeWithTag(PHOTO_TAG).fetchSemanticsNode().boundsInRoot
            // Matted: 88 % of the 236 dp tile, centred in it. Positive control: on the light page it fills the tile.
            assertEquals(236f * 0.88f, photo.width / density.density, 1f, "the photo's width")
            assertEquals(236f * 0.06f, photo.left / density.density, 1f, "the mat's width")
        }

    @Test
    fun `on a light page a raster photo fills its tile`() =
        runComposeUiTest {
            drawCard(card, loader { respond(png(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/png")) })
            awaitLoad()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTagCount(PHOTO_TAG) == 1 }
            assertEquals(236f, onNodeWithTag(PHOTO_TAG).fetchSemanticsNode().boundsInRoot.width / density.density, 1f)
        }

    /** A loader whose every answer is [body] as [type], recording each decoded image's width and kind. */
    private fun recording(
        widths: MutableList<Int>,
        body: Any,
        type: String,
        bitmaps: MutableList<Boolean> = CopyOnWriteArrayList(),
    ): PhotoLoader =
        coilPhotoLoader(
            HttpClient(
                MockEngine {
                    when (body) {
                        is ByteArray -> respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, type))
                        else -> respond(body.toString(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, type))
                    }
                },
            ),
            origin = "http://haul.test",
            events =
                object : EventListener() {
                    override fun onSuccess(
                        request: ImageRequest,
                        result: SuccessResult,
                    ) {
                        widths += result.image.width
                        bitmaps += result.image is BitmapImage
                    }
                },
        )

    @Test
    fun `a photo that failed to load leaves the placeholder tile`() =
        runComposeUiTest {
            drawCard(card, loader { respond("no such photo", HttpStatusCode.NotFound) })
            awaitLoad()
            waitForIdle()
            onNodeWithTag(PLACEHOLDER_TAG).assertExists()
            onNodeWithTag(PHOTO_TAG).assertDoesNotExist()
            assertEquals(1, requests.size, "the photo was never asked for, so its failure was not what was drawn")
        }

    @Test
    fun `a photo still loading shows the placeholder tile`() =
        runComposeUiTest {
            val asked = CompletableDeferred<Unit>()
            drawCard(
                card,
                loader {
                    asked.complete(Unit)
                    awaitCancellation()
                },
            )
            waitUntil(timeoutMillis = 5_000) { asked.isCompleted }
            onNodeWithTag(PLACEHOLDER_TAG).assertExists()
            onNodeWithTag(PHOTO_TAG).assertDoesNotExist()
        }

    @Test
    fun `a card without a photo asks for nothing and shows the placeholder tile`() =
        runComposeUiTest {
            drawCard(card.copy(image = null), loader { error("nothing should be fetched") })
            waitForIdle()
            onNodeWithTag(PLACEHOLDER_TAG).assertExists()
            assertEquals(emptyList(), requests)
        }

    private fun ComposeUiTest.onAllNodesWithTagCount(tag: String): Int =
        androidx.compose.ui.test
            .hasTestTag(tag)
            .let { onAllNodes(it).fetchSemanticsNodes().size }

    /** A small PNG, drawn here: the test downloads nothing. */
    private fun png(): ByteArray {
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            color = Color(0x6E68A6)
            fillRect(0, 0, 16, 16)
            dispose()
        }
        return ByteArrayOutputStream().use {
            ImageIO.write(image, "png", it)
            it.toByteArray()
        }
    }

    private companion object {
        /** A picture as the seed draws one, in miniature: the tone and an object on it. */
        const val SVG =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\" width=\"24\" height=\"24\">" +
                "<rect width=\"24\" height=\"24\" fill=\"#E6E4FF\"/><circle cx=\"12\" cy=\"12\" r=\"6\" fill=\"#232329\"/></svg>"
    }
}
