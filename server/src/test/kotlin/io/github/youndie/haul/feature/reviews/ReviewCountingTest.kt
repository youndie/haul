package io.github.youndie.haul.feature.reviews

import io.github.youndie.haul.feature.reviews.domain.Ratings
import io.github.youndie.haul.feature.reviews.domain.ReviewCommands
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The arithmetic a review is counted with and the name it is signed with. */
class ReviewCountingTest {
    /** B-86: the rating is the histogram's average, to a tenth, half up; nobody's stars are no rating. */
    @Test
    fun `a rating is its histogram's average to a tenth`() {
        assertEquals(BigDecimal("4.5"), Ratings.average(mapOf(4 to 1, 5 to 1)))
        assertEquals(BigDecimal("3.0"), Ratings.average(mapOf(5 to 1, 1 to 1)))
        assertEquals(BigDecimal("4.3"), Ratings.average(mapOf(5 to 1, 4 to 2)))
        // Half up: 4.25 is 4.3.
        assertEquals(BigDecimal("4.3"), Ratings.average(mapOf(5 to 1, 4 to 3)))
        assertNull(Ratings.average(emptyMap()))
        assertNull(Ratings.average(mapOf(5 to 0)))
        // The canvas's bars, 78 / 14 / 4 / 2 / 2 % of 2,341, average 4.64: not the 4.8 every artboard writes.
        assertEquals(BigDecimal("4.6"), Ratings.average(mapOf(5 to 1_826, 4 to 328, 3 to 94, 2 to 47, 1 to 46)))
        // The seed's, 84 / 12 / 2 / 1 / 1 %, do — and one more review of five stars, or of one, leaves it there.
        val seeded = mapOf(5 to 1_966, 4 to 281, 3 to 47, 2 to 24, 1 to 23)
        assertEquals(BigDecimal("4.8"), Ratings.average(seeded))
        assertEquals(BigDecimal("4.8"), Ratings.average(seeded + (5 to 1_967)))
        assertEquals(BigDecimal("4.8"), Ratings.average(seeded + (1 to 24)))
    }

    @Test
    fun `a review is signed with the first name and the last name's initial`() {
        assertEquals("Maya K.", ReviewCommands.authorName("Maya Kowalski"))
        assertEquals("Ana M.", ReviewCommands.authorName("  Ana  de la  Mar "))
        assertEquals("Cher", ReviewCommands.authorName("Cher"))
        assertEquals("A customer", ReviewCommands.authorName(" "))
    }
}
