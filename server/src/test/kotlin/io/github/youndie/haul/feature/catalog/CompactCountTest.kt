package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.feature.catalog.domain.compactCount
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * «12K» the way the canvas writes it (B-52), at every edge of the rule: the number itself below a thousand,
 * one decimal under ten of a unit, whole units from ten. Truncated, so the label never says a unit more than
 * was bought — rounded, 999 would read «1K» and 12,999 «13K».
 */
class CompactCountTest {
    @Test
    fun `counts abbreviate as the canvas writes them`() {
        val expected =
            mapOf(
                0 to "0",
                50 to "50",
                840 to "840",
                999 to "999",
                1_000 to "1K",
                1_050 to "1K",
                1_234 to "1.2K",
                1_999 to "1.9K",
                9_999 to "9.9K",
                10_000 to "10K",
                12_340 to "12K",
                12_999 to "12K",
                600_000 to "600K",
                999_999 to "999K",
                1_000_000 to "1M",
                1_250_000 to "1.2M",
                12_400_000 to "12M",
            )
        assertEquals(expected, expected.mapValues { (value, _) -> compactCount(value) })
    }
}
