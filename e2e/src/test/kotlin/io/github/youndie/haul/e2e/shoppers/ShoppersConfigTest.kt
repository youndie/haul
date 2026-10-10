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
        )

    @Test
    fun `the four addresses and secrets are enough and the rest is gentle by default`() {
        val config = ShoppersConfig.from(minimal)
        assertEquals("http://haul", config.origin)
        assertEquals(listOf("sam@example.com", "maya@example.com"), config.people)
        assertEquals(600.seconds, config.interval)
        assertEquals(30.seconds, config.poll)
        assertEquals(3600.seconds, config.wait)
        assertEquals(4, config.inFlight)
        assertEquals(0, config.walks)
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
    fun `the password is never in what the configuration prints`() {
        val config = ShoppersConfig.from(minimal)
        assertFalse("not-printed-anywhere" in config.toString(), config.toString())
        assertEquals("not-printed-anywhere", config.password.value)
    }
}
