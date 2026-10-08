package io.github.youndie.haul.feature.search.domain

import io.github.youndie.haul.ErrorCode
import java.time.OffsetDateTime

/** What a search route can refuse with; the application answers each with its status and body. */
internal sealed class SearchError(
    val code: ErrorCode,
    override val message: String,
    val field: String? = null,
) : Exception(message) {
    class QueryTooShort : SearchError(ErrorCode.QueryTooShort, "Type at least $MIN_QUERY characters", "q")
}

/** Suggestions and searches start at two characters (feature-search). */
internal const val MIN_QUERY = 2

/**
 * A query as search reads it: trimmed, lower-cased, inner spaces collapsed; refused when shorter than
 * [MIN_QUERY]. The words are what full text matches on, letters and digits only, so nothing the
 * shopper types reaches `to_tsquery` as syntax.
 */
internal class Query private constructor(
    val text: String,
) {
    val words: List<String> = text.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    companion object {
        fun of(raw: String?): Query {
            val text =
                raw
                    .orEmpty()
                    .trim()
                    .lowercase()
                    .replace(Regex("\\s+"), " ")
            if (text.length < MIN_QUERY) throw SearchError.QueryTooShort()
            return Query(text)
        }
    }
}

/** A matching product: its id and the leaf category it is listed in. */
internal data class Match(
    val productId: String,
    val categorySlug: String,
)

/** Full-text and substring matching over the catalog, in the database that holds it. */
internal interface SearchRepository {
    /** The products matching [query], the most relevant first, then the most reviewed. */
    suspend fun matching(query: Query): List<Match>

    /**
     * Up to [limit] queries to suggest for [query]: category, brand and kind names that start with it,
     * then those with a word that does, then the ones spelt like it — the most stocked first.
     */
    suspend fun terms(
        query: Query,
        limit: Int,
    ): List<String>
}

/**
 * A customer's recent searches: the last [KEPT], one per distinct query, newest first. Keyed by the
 * customer's id; a guest has none and nothing is stored for one.
 */
internal interface RecentSearches {
    suspend fun record(
        customerId: String,
        query: String,
        at: OffsetDateTime,
    )

    suspend fun list(customerId: String): List<String>

    suspend fun clear(customerId: String)

    companion object {
        const val KEPT = 10
    }
}
