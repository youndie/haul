package io.github.youndie.haul.feature.reviews.data

import io.github.youndie.haul.feature.catalog.data.ProductsTable
import io.github.youndie.haul.feature.identity.data.CustomersTable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.between
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

// The Exposed side of V12__reviews.sql. `SchemaTest` holds the two together, CHECK constraints included:
// they are declared here under the names PostgreSQL gave them there.

internal object ReviewsTable : Table("reviews") {
    val id = text("id")
    val productId = text("product_id").references(ProductsTable.id)
    val customerId = text("customer_id").references(CustomersTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val author = text("author")
    val rating = integer("rating").check("reviews_rating_check") { it.between(1, 5) }
    val title = text("title")
    val body = text("body")
    val verified = bool("verified")
    val helpful = integer("helpful").default(0)
    val tone = text("tone")
    val photos = jsonb<JsonArray>("photos", Json).default(JsonArray(emptyList()))
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("reviews_one_per_customer", productId, customerId)
    }
}

internal object RatingCountsTable : Table("rating_counts") {
    val productId = text("product_id").references(ProductsTable.id)
    val stars = integer("stars").check("rating_counts_stars_check") { it.between(1, 5) }
    val count = integer("count").check("rating_counts_count_check") { it greaterEq 0 }
    override val primaryKey = PrimaryKey(productId, stars, name = "pk_rating_counts")
}

internal object QuestionsTable : Table("questions") {
    val id = text("id")
    val productId = text("product_id").references(ProductsTable.id)
    val customerId = text("customer_id").references(CustomersTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val text = text("text")
    val askedAt = timestampWithTimeZone("asked_at")
    val answer = text("answer").nullable()
    val answeredAt = timestampWithTimeZone("answered_at").nullable()
    override val primaryKey = PrimaryKey(id)

    init {
        index("questions_product_id", false, productId)
        check("questions_answered_together") {
            (answer.isNull() and answeredAt.isNull()) or (answer.isNotNull() and answeredAt.isNotNull())
        }
    }
}

internal val reviewTables: List<Table> = listOf(ReviewsTable, RatingCountsTable, QuestionsTable)
