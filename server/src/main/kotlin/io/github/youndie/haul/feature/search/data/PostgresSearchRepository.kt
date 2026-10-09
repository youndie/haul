package io.github.youndie.haul.feature.search.data

import io.github.youndie.haul.feature.search.domain.Match
import io.github.youndie.haul.feature.search.domain.Query
import io.github.youndie.haul.feature.search.domain.SearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import javax.sql.DataSource

/**
 * Search over PostgreSQL full text and `pg_trgm` (research, open question 1), in plain SQL: the
 * expressions are the ones `V3__search.sql` and `V25__listing_name.sql` index, character for character,
 * or the planner does not use the index.
 */
internal class PostgresSearchRepository(
    private val dataSource: DataSource,
) : SearchRepository {
    override suspend fun matching(query: Query): List<Match> {
        if (query.words.isEmpty()) return emptyList()
        return sql { connection ->
            connection.prepareStatement(MATCHING).use { statement ->
                statement.setString(1, tsQuery(query))
                statement.setString(2, "%${like(query.text)}%")
                statement.setString(3, "%${like(query.text)}%")
                statement.setString(4, tsQuery(query))
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
        // What the cards write (B-45, V25): the listing name, the title when there is none.
        const val DOCUMENT =
            "to_tsvector('english', coalesce(p.listing_name, p.title) || ' ' || p.brand || ' ' || coalesce(p.kind, ''))"

        val MATCHING =
            """
            SELECT p.id, p.category_slug
            FROM products p
            WHERE $DOCUMENT @@ to_tsquery('english', ?) OR lower(p.title) LIKE ? OR lower(p.listing_name) LIKE ?
            ORDER BY ts_rank($DOCUMENT, to_tsquery('english', ?)) DESC, p.reviews_count DESC, p.id
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
