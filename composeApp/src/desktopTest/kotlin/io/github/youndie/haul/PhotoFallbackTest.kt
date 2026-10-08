package io.github.youndie.haul

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import coil3.EventListener
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import io.github.youndie.haul.ui.LocalPhotoLoader
import io.github.youndie.haul.ui.PHOTO_TAG
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
            productId = "p-sony-wh-1000xm6",
            title = "Sony WH-1000XM6",
            price = "$349",
            rating = "4.8",
            reviews = "2,341",
            delivery = "Tomorrow",
            tone = "#E6E4FF",
            label = "headphones",
            image = "/images/products/p-sony-wh-1000xm6/1.png",
        )
    private val placeholderLabel = "HEADPHONES"
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
    ) = setContent {
        Fixture(compact = false) {
            CompositionLocalProvider(LocalPhotoLoader provides photos) {
                Box(Modifier.width(236.dp)) { ProductCardView(shown) }
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
            onNodeWithText(placeholderLabel).assertDoesNotExist()
            // The server's path, resolved against the page's origin: Coil takes a bare path for a file.
            assertEquals(listOf("http://haul.test/images/products/p-sony-wh-1000xm6/1.png"), requests)
        }

    @Test
    fun `a photo that failed to load leaves the placeholder tile`() =
        runComposeUiTest {
            drawCard(card, loader { respond("no such photo", HttpStatusCode.NotFound) })
            awaitLoad()
            waitForIdle()
            onNodeWithText(placeholderLabel).assertExists()
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
            onNodeWithText(placeholderLabel).assertExists()
            onNodeWithTag(PHOTO_TAG).assertDoesNotExist()
        }

    @Test
    fun `a card without a photo asks for nothing and shows the placeholder tile`() =
        runComposeUiTest {
            drawCard(card.copy(image = null), loader { error("nothing should be fetched") })
            waitForIdle()
            onNodeWithText(placeholderLabel).assertExists()
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
}
