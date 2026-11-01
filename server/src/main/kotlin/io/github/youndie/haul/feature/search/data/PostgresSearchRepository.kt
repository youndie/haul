package io.github.youndie.haul.feature.search.data

import io.github.youndie.haul.feature.search.domain.Match
import io.github.youndie.haul.feature.search.domain.Query
import io.github.youndie.haul.feature.search.domain.SearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import javax.sql.DataSource

/**
 * Search over PostgreSQL full text and `pg_trgm` (research, open question 1), in plain SQL. A product is matched
 * on two documents (B-171): its name — the listing name, the title when there is none, and the brand — weighted
 * `A`, and what it is — its kind, its features («Wireless»), its category's name and that category's parent's
 * («Headphones», «Audio») — weighted `B`. Every typed word is required, in either. A product whose name holds
 * every word, or whose title or listing name holds the query as typed, comes before one found only by what it
 * is; inside each, `ts_rank` over both documents, then the most reviewed.
 *
 * The category names come from a join, so the matched document is no expression `V3__search.sql` or
 * `V25__listing_name.sql` could index; at the seed's size the planner used neither of their indexes anyway
 * (B-09), and the next step at a marketplace's size is a stored, weighted `tsvector` column.
 */
internal class PostgresSearchRepository(
    private val dataSource: DataSource,
) : SearchRepository {
    override suspend fun matching(query: Query): List<Match> {
        if (query.words.isEmpty()) return emptyList()
        return sql { connection ->
            connection.prepareStatement(MATCHING).use { statement ->
                statement.setString(1, "%${like(query.text)}%")
                statement.setString(2, "%${like(query.text)}%")
                statement.setString(3, tsQuery(query))
                statement.executeQuery().use { rows ->
                    buildList { while (rows.next()) add(Match(rows.getString(1), rows.getString(2))) }
                }
            }
        }
    }

    override suspend fun terms(
        query: Query,
        limit: Int,
    ): List<String> =
        sql { connection ->
            connection.prepareStatement(TERMS).use { statement ->
                val text = like(query.text)
                statement.setString(1, "$text%")
                statement.setString(2, "% $text%")
                statement.setString(3, query.text)
                statement.setString(4, "$text%")
                statement.setString(5, query.text)
                statement.setInt(6, limit)
                statement.executeQuery().use { rows ->
                    buildList { while (rows.next()) add(rows.getString(1)) }
                }
            }
        }

    private suspend fun <T> sql(block: (Connection) -> T): T =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                try {
                    block(connection).also { connection.commit() }
                } catch (e: Exception) {
                    connection.rollback()
                    throw e
                }
            }
        }

    /** Every word a prefix, all of them required: «running sh» is `running:* & sh:*`. */
    private fun tsQuery(query: Query): String = query.words.joinToString(" & ") { "$it:*" }

    private fun like(text: String): String = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private companion object {
        // What the cards write (B-45, V25): the listing name, the title when there is none; and the brand.
        const val NAME = "to_tsvector('english', coalesce(p.listing_name, p.title) || ' ' || p.brand)"

        // What the product is (B-171): its kind, its features (a JSON array, whose punctuation full text drops),
        // its leaf category's name and the name of the category above it.
        const val KIND =
            "to_tsvector('english', coalesce(p.kind, '') || ' ' || p.features::text || ' ' || c.name || ' ' || " +
                "coalesce(parent.name, ''))"

        val MATCHING =
            """
            SELECT m.id, m.category_slug
            FROM (
                SELECT p.id, p.category_slug, p.reviews_count, $NAME AS name_doc, $KIND AS kind_doc,
                       lower(p.title) LIKE ? OR coalesce(lower(p.listing_name) LIKE ?, false) AS spelt
                FROM products p
                JOIN categories c ON c.slug = p.category_slug
                LEFT JOIN categories parent ON parent.slug = c.parent_slug
            ) m
            CROSS JOIN LATERAL (SELECT setweight(m.name_doc, 'A') || setweight(m.kind_doc, 'B') AS document) d
            CROSS JOIN to_tsquery('english', ?) q
            WHERE d.document @@ q OR m.spelt
            ORDER BY (m.name_doc @@ q OR m.spelt) DESC, ts_rank(d.document, q) DESC, m.reviews_count DESC, m.id
            """.trimIndent()

        val TERMS =
            """
            WITH terms(term, n) AS (
                SELECT lower(c.name), count(p.id) FROM categories c JOIN products p ON p.category_slug = c.slug GROUP BY lower(c.name)
                UNION ALL
                SELECT lower(p.brand), count(*) FROM products p GROUP BY lower(p.brand)
                UNION ALL
                SELECT lower(p.kind), count(*) FROM products p WHERE p.kind IS NOT NULL GROUP BY lower(p.kind)
            )
            SELECT term
            FROM terms
            GROUP BY term
            HAVING term LIKE ? OR term LIKE ? OR similarity(term, ?) > 0.3
            ORDER BY term LIKE ? DESC, sum(n) DESC, similarity(term, ?) DESC, term
            LIMIT ?
            """.trimIndent()
    }
}
