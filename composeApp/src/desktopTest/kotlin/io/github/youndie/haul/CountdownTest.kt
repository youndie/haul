package io.github.youndie.haul

import io.github.youndie.haul.ui.countdown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** The deals countdown is computed by the client from the instant the server sends and a given «now». */
class CountdownTest {
    /**
     * The server writes the instant through `OffsetDateTime`, which drops zero seconds
     * («2025-10-08T00:00-04:00»); `Instant.parse` alone refuses that, and the row would crash the page.
     */
    @Test
    fun `the canvas reads 04 12 37 at its now`() {
        assertEquals("04:12:37", countdown("2025-10-08T00:00-04:00", CANVAS_NOW))
    }

    @Test
    fun `a countdown that has run out stays at zero`() {
        assertEquals("00:00:00", countdown("2025-10-07T19:00:00-04:00", CANVAS_NOW))
    }

    @Test
    fun `more than a day left is counted in hours`() {
        assertEquals("25:00:00", countdown("2025-10-08T20:47:23-04:00", Instant.parse("2025-10-07T19:47:23-04:00")))
    }
}
