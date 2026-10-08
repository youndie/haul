package io.github.youndie.haul.feature.reviews

import io.github.youndie.haul.feature.reviews.data.ExposedReviews
import io.github.youndie.haul.feature.reviews.domain.ReviewCommands
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

/** The arithmetic a review is counted with and the name it is signed with. */
class ReviewCountingTest {
    @Test
    fun `a review moves the average by its share, to a tenth`() {
        assertEquals(BigDecimal("4.5"), ExposedReviews.average(BigDecimal("4.0"), 1, 5))
        assertEquals(BigDecimal("3.0"), ExposedReviews.average(BigDecimal("5.0"), 1, 1))
        assertEquals(BigDecimal("5.0"), ExposedReviews.average(BigDecimal("0.0"), 0, 5))
        // The headphones' 4.8 over 2,341 reviews stays 4.8 after one more of five stars, and of one.
        assertEquals(BigDecimal("4.8"), ExposedReviews.average(BigDecimal("4.8"), 2_341, 5))
        assertEquals(BigDecimal("4.8"), ExposedReviews.average(BigDecimal("4.8"), 2_341, 1))
    }

    @Test
    fun `a review is signed with the first name and the last name's initial`() {
        assertEquals("Maya K.", ReviewCommands.authorName("Maya Kowalski"))
        assertEquals("Ana M.", ReviewCommands.authorName("  Ana  de la  Mar "))
        assertEquals("Cher", ReviewCommands.authorName("Cher"))
        assertEquals("A customer", ReviewCommands.authorName(" "))
    }
}
