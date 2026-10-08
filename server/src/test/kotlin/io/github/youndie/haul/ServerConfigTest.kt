package io.github.youndie.haul

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerConfigTest {
    private fun config(vararg pairs: Pair<String, String>) = ServerConfig(mapOf(*pairs)::get)

    @Test
    fun `no agent variables leave every agent off`() {
        val settings = config().observability

        assertNull(settings.metrik)
        assertNull(settings.tracy)
        assertNull(settings.katcher)
        assertEquals("dev", settings.release)
    }

    @Test
    fun `an endpoint and a key turn an agent on, under the commit as the release`() {
        val settings =
            config(
                "HAUL_COMMIT" to "abc1234",
                "HAUL_TRACY_ENDPOINT" to "https://tracy.example",
                "HAUL_TRACY_KEY" to "k",
                "HAUL_TRACY_SAMPLE_RATE" to "1.0",
                "HOSTNAME" to "haul-7d9f",
            ).observability

        assertEquals("https://tracy.example", settings.tracy?.endpoint)
        assertEquals(1.0, settings.tracySampleRate)
        assertEquals("abc1234", settings.release)
        assertEquals("haul-7d9f", settings.instance)
    }

    /** Half an agent is refused at start: one that believes it is observed and is silent looks like one that works. */
    @Test
    fun `an endpoint without its key refuses the start`() {
        val error =
            assertFailsWith<IllegalStateException> { config("HAUL_METRIK_ENDPOINT" to "metrik:9999").observability }

        assertTrue("HAUL_METRIK_KEY" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `a key without its endpoint refuses the start`() {
        assertFailsWith<IllegalStateException> { config("HAUL_KATCHER_KEY" to "k").observability }
    }

    @Test
    fun `a sample rate outside 0 to 1 refuses the start`() {
        assertFailsWith<IllegalStateException> {
            config(
                "HAUL_TRACY_ENDPOINT" to "https://tracy.example",
                "HAUL_TRACY_KEY" to "k",
                "HAUL_TRACY_SAMPLE_RATE" to "5",
            ).observability
        }
    }

    @Test
    fun `the agent key never reaches a log line through toString`() {
        val tracy =
            config(
                "HAUL_TRACY_ENDPOINT" to "https://tracy.example",
                "HAUL_TRACY_KEY" to "s3cret",
            ).observability.tracy

        assertTrue("s3cret" !in tracy.toString())
    }

    @Test
    fun `no web directory serves no page, a named missing one refuses the start`() {
        assertNull(config().webDir)
        assertFailsWith<IllegalArgumentException> { config("HAUL_WEB_DIR" to "/nonexistent/haul-web").webDir }

        val dir = Files.createTempDirectory("haul-web").toFile()
        assertEquals(dir, config("HAUL_WEB_DIR" to dir.path).webDir)
    }

    /** Object storage (B-30) is optional as a whole: no endpoint, no photos, and the start goes on. */
    @Test
    fun `no object storage endpoint is no photos`() {
        assertNull(config().photos)
    }

    /** A half-configured store must stop the start, not surface later as photos that never load. */
    @Test
    fun `an object storage endpoint without its bucket refuses the start`() {
        val error =
            assertFailsWith<IllegalStateException> {
                config(
                    "HAUL_S3_ENDPOINT" to "http://s3:9000",
                    "HAUL_S3_ACCESS_KEY" to "a",
                    "HAUL_S3_SECRET_KEY" to "s",
                ).photos
            }
        assertTrue("HAUL_S3_BUCKET" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `the object storage secret never reaches a log line through toString`() {
        val photos =
            config(
                "HAUL_S3_ENDPOINT" to "http://s3:9000",
                "HAUL_S3_BUCKET" to "photos",
                "HAUL_S3_ACCESS_KEY" to "access",
                "HAUL_S3_SECRET_KEY" to "very-secret",
            ).photos
        assertEquals("photos", photos?.bucket)
        assertEquals("us-east-1", photos?.region)
        assertTrue("very-secret" !in photos.toString(), "the config prints its secret: $photos")
    }

    @Test
    fun `no sign-in variables leave sign-in off`() {
        assertNull(config().signIn)
    }

    @Test
    fun `an issuer and a client turn sign-in on, the realm read off the issuer`() {
        val signIn =
            config(
                "HAUL_OIDC_ISSUER" to "http://127.0.0.1:18081/realms/haul",
                "HAUL_OIDC_CLIENT_ID" to "haul-web",
            ).signIn!!

        assertEquals("http://127.0.0.1:18081", signIn.base)
        assertEquals("haul", signIn.realm)
        assertEquals("http://127.0.0.1:18081/realms/haul", signIn.settings().issuer)
        assertEquals("haul-web", signIn.settings().clientId)
    }

    @Test
    fun `half of sign-in, or an issuer that is not a realm's, refuses the start`() {
        assertFailsWith<IllegalStateException> { config("HAUL_OIDC_ISSUER" to "http://h/realms/haul").signIn }
        assertFailsWith<IllegalStateException> { config("HAUL_OIDC_CLIENT_ID" to "haul-web").signIn }
        assertFailsWith<IllegalArgumentException> {
            config("HAUL_OIDC_ISSUER" to "http://h/haul", "HAUL_OIDC_CLIENT_ID" to "haul-web").signIn
        }
    }
}
