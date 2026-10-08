package io.github.youndie.haul.feature.search

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.search.data.ExposedRecentSearches
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.testing.PostgresHarness
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The storage half of «Recent searches are personal» and of the rules on recent searches. The scenario
 * itself needs two signed-in customers, and sign-in arrives with B-12; until then this is where the
 * rule is held.
 */
class RecentSearchesTest {
    private val recent = ExposedRecentSearches(Databases.connect(PostgresHarness.freshDatabase()))
    private val now = CatalogSeed.NOW

    @Test
    fun `one customer's searches are not another's`() =
        runBlocking {
            recent.record("c-ann", "wireless earbuds", now)
            recent.record("c-bob", "running shoes", now)
            assertEquals(listOf("running shoes"), recent.list("c-bob"))
            // Positive control: the search was recorded, for the customer who made it.
            assertEquals(listOf("wireless earbuds"), recent.list("c-ann"))
        }

    @Test
    fun `the last ten are kept newest first and a repeat moves to the top`() =
        runBlocking {
            (1..12).forEach { recent.record("c-cy", "query $it", now.plusSeconds(it.toLong())) }
            recent.record("c-cy", "query 5", now.plusSeconds(13))
            val list = recent.list("c-cy")
            assertEquals(10, list.size)
            assertEquals(listOf("query 5", "query 12", "query 11"), list.take(3))
            // 1 and 2 fell out when 11 and 12 came; repeating 5 added nothing, so 3 is still the oldest.
            assertEquals(listOf("query 3"), list.takeLast(1), "the oldest kept are not the ten newest")
        }

    @Test
    fun `clear empties one customer's list`() =
        runBlocking {
            recent.record("c-di", "mugs", now)
            recent.record("c-ed", "pillows", now)
            recent.clear("c-di")
            assertEquals(emptyList(), recent.list("c-di"))
            assertEquals(listOf("pillows"), recent.list("c-ed"))
        }
}
