package io.github.youndie.haul.e2e.shoppers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

/** What the shoppers' environment may say, and what it must not (B-31). */
class ShoppersConfigTest {
    private val minimal =
        mapOf(
            ShoppersConfig.ORIGIN to "http://haul/",
            ShoppersConfig.REDIRECT to "https://haul.example/signed-in.html",
            ShoppersConfig.PEOPLE to "sam@example.com, maya@example.com",
            ShoppersConfig.PASSWORD to "not-printed-anywhere",
            ShoppersConfig.TEST_CARD_KEY to "not-printed-either",
        )

    @Test
    fun `the addresses and secrets are enough and the rest is gentle by default`() {
        val config = ShoppersConfig.from(minimal)
        assertEquals("http://haul", config.origin)
        assertEquals(listOf("sam@example.com", "maya@example.com"), config.people)
        assertEquals(600.seconds, config.interval)
        assertEquals(30.seconds, config.poll)
        assertEquals(3600.seconds, config.wait)
        assertEquals(4, config.inFlight)
        assertEquals(0, config.walks)
        assertEquals(10, config.declineEvery)
        assertEquals(
            listOf(10, 20),
            (1..20).filter(config::declines),
            "one walk in ten pays with the declining card, never the first",
        )
    }

    /** B-89: no declining walks needs no key; any needs it, refused by its name. */
    @Test
    fun `declining walks need the test card's key and none need none`() {
        val none = ShoppersConfig.from(minimal - ShoppersConfig.TEST_CARD_KEY + (ShoppersConfig.DECLINE_EVERY to "0"))
        assertEquals(null, none.testCardKey)
        assertEquals(emptyList(), (1..20).filter(none::declines))
        val every = ShoppersConfig.from(minimal + (ShoppersConfig.DECLINE_EVERY to "1"))
        assertEquals((1..3).toList(), (1..3).filter(every::declines))
        val refused =
            assertFailsWith<IllegalArgumentException> { ShoppersConfig.from(minimal - ShoppersConfig.TEST_CARD_KEY) }
        assertEquals("${ShoppersConfig.TEST_CARD_KEY} is required", refused.message)
        assertFailsWith<IllegalArgumentException> {
            ShoppersConfig.from(
                minimal + (ShoppersConfig.DECLINE_EVERY to "-1"),
            )
        }
    }

    @Test
    fun `the rate and the waits are read in seconds`() {
        val config =
            ShoppersConfig.from(
                minimal +
                    mapOf(
                        ShoppersConfig.INTERVAL to "120",
                        ShoppersConfig.POLL to "5",
                        ShoppersConfig.WAIT to "900",
                        ShoppersConfig.IN_FLIGHT to "2",
                        ShoppersConfig.WALKS to "3",
                    ),
            )
        assertEquals(120.seconds, config.interval)
        assertEquals(5.seconds, config.poll)
        assertEquals(900.seconds, config.wait)
        assertEquals(2, config.inFlight)
        assertEquals(3, config.walks)
    }

    @Test
    fun `a missing variable is refused by its name`() {
        for (name in minimal.keys) {
            val refused = assertFailsWith<IllegalArgumentException> { ShoppersConfig.from(minimal - name) }
            assertEquals("$name is required", refused.message)
        }
        val blank =
            assertFailsWith<IllegalArgumentException> {
                ShoppersConfig.from(minimal + (ShoppersConfig.PEOPLE to " , "))
            }
        assertEquals("${ShoppersConfig.PEOPLE} names nobody", blank.message)
    }

    @Test
    fun `a rate that is not a positive whole number is refused`() {
        for (value in listOf("0", "-5", "fast", "1.5")) {
            assertFailsWith<IllegalArgumentException>(value) {
                ShoppersConfig.from(minimal + (ShoppersConfig.INTERVAL to value))
            }
        }
        assertFailsWith<IllegalArgumentException> {
            ShoppersConfig.from(
                minimal + (ShoppersConfig.ORIGIN to "haul:8080"),
            )
        }
    }

    @Test
    fun `the password and the key are never in what the configuration prints`() {
        val config = ShoppersConfig.from(minimal)
        assertFalse("not-printed-anywhere" in config.toString(), config.toString())
        assertFalse("not-printed-either" in config.toString(), config.toString())
        assertEquals("not-printed-anywhere", config.password.value)
        assertEquals("not-printed-either", config.testCardKey?.value)
    }
}
