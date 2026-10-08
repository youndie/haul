package io.github.youndie.haul.feature.catalog.data

import io.github.youndie.haul.seed.SeedPhotos
import io.github.youndie.haul.testing.SeaweedHarness
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The photo store against a real S3 server — SeaweedFS, which checks every signature it is sent. */
class S3PhotoStoreTest {
    /**
     * What the seed writes the photo route reads back, byte for byte and with its media type. A
     * signature the server refuses, a key encoded differently on the two calls, or a body the client
     * re-encodes would each fail here and nowhere else before a shopper's broken photo.
     */
    @Test
    fun `a stored photo reads back byte for byte`() =
        runBlocking<Unit> {
            val store = SeaweedHarness.store()
            val png = SeedPhotos.draw("#E6E4FF")
            val key = SeedPhotos.keyFor("p-sony-wh-1000xm6", png)

            store.put(key, png, "image/png")
            val read = assertNotNull(store.get(key), "the stored photo is not there")

            assertContentEquals(png, read.bytes)
            assertEquals("image/png", read.contentType)
        }

    /** «Not there» is `null`, the route's 404 — not an exception, which would be its 500. */
    @Test
    fun `a missing photo is null`() =
        runBlocking<Unit> {
            val store = SeaweedHarness.store()
            assertNull(store.get("products/nobody/0000000000000000.png"))
            // Positive control: the same store does find what was put, so null meant «absent».
            store.put("products/somebody/1.png", byteArrayOf(1, 2, 3), "image/png")
            assertNotNull(store.get("products/somebody/1.png"))
        }

    /**
     * The control on the harness: a wrong secret is refused. Without it a server that let anonymous
     * requests through would pass every test above while the signer signed nothing right.
     */
    @Test
    fun `a request signed with the wrong secret is refused`() =
        runBlocking<Unit> {
            val bucket = SeaweedHarness.bucket()
            val wrong = SeaweedHarness.store(bucket.copy(secretKey = "not-the-secret"))
            val failure =
                assertFailsWith<PhotoStoreException> { wrong.put("products/x/1.png", byteArrayOf(1), "image/png") }
            assertTrue("403" in failure.message.orEmpty(), "expected a 403, got: ${failure.message}")
        }
}
