package io.github.youndie.haul.feature.identity.data

import io.github.youndie.haul.feature.identity.domain.DemoShopperRepository
import io.github.youndie.haul.feature.order.data.OrdersTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.Connection
import java.time.OffsetDateTime

/**
 * Demo shoppers over Exposed (V31). The sweep is SQL of its own, in one transaction: it reaches into every
 * feature's tables, because a demo shopper's traces are in all of them, and half a sweep would leave a product
 * counting a review nobody can see or a window holding a place for an order that is gone.
 */
internal class ExposedDemoShoppers(
    private val database: Database,
) : DemoShopperRepository {
    private suspend fun <T> tx(block: JdbcTransaction.() -> T): T =
        withContext(Dispatchers.IO) { transaction(database) { block() } }

    override suspend fun madeSince(at: OffsetDateTime): Int =
        tx {
            DemoShoppersTable
                .selectAll()
                .where { DemoShoppersTable.createdAt greaterEq at }
                .count()
                .toInt()
        }

    override suspend fun record(
        id: String,
        at: OffsetDateTime,
    ) {
        tx {
            DemoShoppersTable.insert {
                it[customerId] = id
                it[createdAt] = at
            }
        }
    }

    override suspend fun due(before: OffsetDateTime): List<String> =
        tx {
            val placing =
                DemoShoppersTable
                    .join(
                        OrdersTable,
                        JoinType.INNER,
                        DemoShoppersTable.customerId,
                        OrdersTable.customerId,
                        additionalConstraint = { OrdersTable.status eq PLACING },
                    ).select(DemoShoppersTable.customerId)
                    .where { DemoShoppersTable.createdAt less before }
                    .map { it[DemoShoppersTable.customerId] }
                    .toSet()
            DemoShoppersTable
                .selectAll()
                .where { DemoShoppersTable.createdAt less before }
                .orderBy(DemoShoppersTable.createdAt)
                .map { it[DemoShoppersTable.customerId] }
                .filterNot { it in placing }
        }

    override suspend fun forget(id: String): Boolean =
        tx {
            val jdbc = connection.connection as Connection
            // The customer's row locked first: two sweeps of one shopper, or a sweep beside a placement, take turns.
            val present = jdbc.rows(LOCK, id) > 0
            if (!present || jdbc.rows(PLACING_ORDER, id) > 0) return@tx false
            SWEEP.forEach { statement -> jdbc.update(statement, id) }
            true
        }

    private fun Connection.rows(
        sql: String,
        id: String,
    ): Int =
        prepareStatement(sql).use { statement ->
            statement.setString(1, id)
            statement.executeQuery().use { rows -> generateSequence { if (rows.next()) 1 else null }.count() }
        }

    /** [sql], with every `?` the shopper's id. */
    private fun Connection.update(
        sql: String,
        id: String,
    ) {
        prepareStatement(sql).use { statement ->
            repeat(sql.count { it == '?' }) { statement.setString(it + 1, id) }
            statement.executeUpdate()
        }
    }

    private companion object {
        const val PLACING = "placing"

        const val LOCK =
            "SELECT 1 FROM demo_shoppers d JOIN customers c ON c.id = d.customer_id " +
                "WHERE d.customer_id = ? FOR UPDATE OF c"

        const val PLACING_ORDER = "SELECT 1 FROM orders WHERE customer_id = ? AND status = '$PLACING'"

        /**
         * In order: what the orders hold elsewhere — the units given back to the shelf, less those a return already
         * put back; the windows' places; the payments, the saga's rows and its idempotency keys — then the votes,
         * reviews and questions with the counts they moved, the searches, and last the customer, whose cascades take
         * the orders (lines, shipments and their events, returns, Haul Pay plans), the cart, the Saved list, the
         * views, the addresses, the checkout, the membership, the points and the demo shopper's own row.
         */
        val SWEEP =
            listOf(
                """
                UPDATE skus s SET stock = s.stock + b.units
                FROM (
                    SELECT r.sku_id, sum(greatest(r.quantity - coalesce(x.quantity, 0), 0))::int AS units
                    FROM stock_reservations r
                    JOIN orders o ON o.id = r.holder
                    LEFT JOIN stock_restocks x ON x.key = 'return:' || r.holder AND x.sku_id = r.sku_id
                    WHERE o.customer_id = ?
                    GROUP BY r.sku_id
                ) b
                WHERE s.id = b.sku_id AND b.units > 0
                """.trimIndent(),
                "DELETE FROM stock_reservations r USING orders o WHERE o.id = r.holder AND o.customer_id = ?",
                "DELETE FROM stock_restocks x USING orders o WHERE x.key = 'return:' || o.id AND o.customer_id = ?",
                """
                UPDATE delivery_slots d SET taken = d.taken - h.n
                FROM (
                    SELECT sr.day, sr.start_hour, count(*)::int AS n
                    FROM slot_reservations sr JOIN orders o ON o.id = sr.holder
                    WHERE o.customer_id = ?
                    GROUP BY sr.day, sr.start_hour
                ) h
                WHERE d.day = h.day AND d.start_hour = h.start_hour
                """.trimIndent(),
                "DELETE FROM slot_reservations sr USING orders o WHERE o.id = sr.holder AND o.customer_id = ?",
                "DELETE FROM payment_captures c USING orders o WHERE c.order_id = o.id AND o.customer_id = ?",
                "DELETE FROM payment_refunds f USING orders o WHERE f.order_id = o.id AND o.customer_id = ?",
                "DELETE FROM payment_authorisations a USING orders o WHERE a.order_id = o.id AND o.customer_id = ?",
                "DELETE FROM petiches p USING orders o WHERE p.id = o.saga_id AND o.customer_id = ?",
                "DELETE FROM idempotency_keys k USING orders o WHERE k.key = o.saga_id AND o.customer_id = ?",
                // «Helpful» they pressed on other people's reviews: one vote a review, so one less each.
                "UPDATE reviews r SET helpful = r.helpful - 1 FROM helpful_votes v WHERE v.review_id = r.id AND v.customer_id = ?",
                "DELETE FROM helpful_votes WHERE customer_id = ?",
                "DELETE FROM helpful_votes v USING reviews r WHERE v.review_id = r.id AND r.customer_id = ?",
                // Their reviews out of the histogram and the count; the average is the histogram's again (B-86).
                """
                UPDATE rating_counts c SET count = c.count - g.n
                FROM (SELECT product_id, rating, count(*)::int AS n FROM reviews WHERE customer_id = ? GROUP BY product_id, rating) g
                WHERE c.product_id = g.product_id AND c.stars = g.rating
                """.trimIndent(),
                """
                UPDATE products p SET reviews_count = p.reviews_count - g.n
                FROM (SELECT product_id, count(*)::int AS n FROM reviews WHERE customer_id = ? GROUP BY product_id) g
                WHERE p.id = g.product_id
                """.trimIndent(),
                """
                UPDATE products p SET rating = c.rating
                FROM (
                    SELECT product_id, round(sum(stars * count)::numeric / sum(count), 1) AS rating
                    FROM rating_counts
                    WHERE product_id IN (SELECT product_id FROM reviews WHERE customer_id = ?)
                    GROUP BY product_id HAVING sum(count) > 0
                ) c
                WHERE c.product_id = p.id
                """.trimIndent(),
                "DELETE FROM reviews WHERE customer_id = ?",
                """
                UPDATE products p SET questions_count = p.questions_count - g.n
                FROM (SELECT product_id, count(*)::int AS n FROM questions WHERE customer_id = ? GROUP BY product_id) g
                WHERE p.id = g.product_id
                """.trimIndent(),
                "DELETE FROM questions WHERE customer_id = ?",
                "DELETE FROM recent_searches WHERE customer_id = ?",
                "DELETE FROM customers WHERE id = ?",
            )
    }
}
