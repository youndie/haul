package io.github.youndie.haul

import io.github.youndie.haul.registry.haulJson
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.kompot.KompotComponent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.PolymorphicSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The client reads what the server writes. The screenshot bodies were written by hand with their nulls
 * spelled out, so only a body encoded the server's way shows what the client accepts: the stand's guest
 * header, which carries no `customerName`, was refused by every screen until B-12 gave the field a
 * default — and a nullable field added without one would be refused again unless the client reads an
 * absent field as null, the way the server writes it.
 */
class WireDecodeTest {
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `the client reads with the settings the server writes with`() {
        val server = haulWireJson.configuration
        val client = haulJson.configuration
        assertEquals(server.explicitNulls, client.explicitNulls, "a null the server leaves out is a null")
        assertEquals(server.classDiscriminator, client.classDiscriminator)
        assertTrue(client.ignoreUnknownKeys, "a field from a newer server is skipped, not refused")
    }

    @Test
    fun `a guest's header sent without its null fields decodes`() {
        val guest =
            HaulHeader(
                id = "header",
                deliverTo = "Brooklyn, NY 11211",
                deliveryPromise = "Free delivery over $35",
                customerName = null,
                cartCount = 0,
                searchPlaceholder = "Search 2.4 million products",
                categories = listOf("Electronics"),
            )
        val body = haulWireJson.encodeToString(PolymorphicSerializer(KompotComponent::class), guest)
        assertFalse("customerName" in body, "the server's encoding leaves the null out: $body")
        assertEquals(guest, haulJson.decodeFromString(PolymorphicSerializer(KompotComponent::class), body))
    }
}
