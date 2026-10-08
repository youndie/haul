package io.github.youndie.haul.feature.catalog.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

// The Exposed side of V1__catalog.sql. `SchemaTest` holds the two to each other: a column declared
// here and missing there fails that test, not the first request that reads it.

internal object CategoriesTable : Table("categories") {
    val slug = text("slug")
    val parentSlug = text("parent_slug").references(slug).nullable()
    val name = text("name")
    val position = integer("position")
    val tone = text("tone")
    val label = text("label")
    override val primaryKey = PrimaryKey(slug)
}

internal object SellersTable : Table("sellers") {
    val id = text("id")
    val name = text("name")
    val rating = decimal("rating", 2, 1)
    val positivePercent = integer("positive_percent")
    val yearsOnHaul = integer("years_on_haul")
    override val primaryKey = PrimaryKey(id)
}

internal object ProductsTable : Table("products") {
    val id = text("id")
    val sellerId = text("seller_id").references(SellersTable.id)
    val categorySlug = text("category_slug").references(CategoriesTable.slug)
    val title = text("title")
    val brand = text("brand")
    val description = text("description")

    // An array of `{"key", "value"}` pairs, not an object: `jsonb` reorders an object's keys, and the
    // specifications are an ordered list on the page.
    val specifications = jsonb<JsonArray>("specifications", Json)
    val rating = decimal("rating", 2, 1)
    val reviewsCount = integer("reviews_count")
    val questionsCount = integer("questions_count")
    val tone = text("tone")
    val label = text("label")
    val createdAt = timestampWithTimeZone("created_at")
    val features = jsonb<JsonArray>("features", Json).default(JsonArray(emptyList()))
    val kind = text("kind").nullable()
    val dispatchDays = integer("dispatch_days").default(0)

    // The description tab's headline and the part of it in the accent face (V6, which also checks that
    // the accent occurs in the headline).
    val headline = text("headline")
    val headlineAccent = text("headline_accent").nullable()

    // V7: the key of the product's photo in the object storage; null is the placeholder tile.
    val imageKey = text("image_key").nullable()
    override val primaryKey = PrimaryKey(id)

    init {
        index("products_category", false, categorySlug)
        check("products_headline_accent_in_headline") {
            headlineAccent.isNull() or
                (CustomFunction("strpos", IntegerColumnType(), headline, headlineAccent) greater 0)
        }
    }
}

internal object SkusTable : Table("skus") {
    val id = text("id")
    val productId = text("product_id").references(ProductsTable.id)
    val position = integer("position")
    val optionValues = jsonb<JsonObject>("options", Json)
    val priceCents = integer("price_cents")
    val oldPriceCents = integer("old_price_cents").nullable()
    val stock = integer("stock")
    override val primaryKey = PrimaryKey(id)

    init {
        index("skus_product", false, productId)
    }
}

internal object CampaignsTable : Table("campaigns") {
    val slug = text("slug")
    val title = text("title")
    val subtitle = text("subtitle")
    val position = integer("position")
    val tone = text("tone")
    val startsAt = timestampWithTimeZone("starts_at")
    val endsAt = timestampWithTimeZone("ends_at")
    val plusEarlyAccessAt = timestampWithTimeZone("plus_early_access_at").nullable()
    override val primaryKey = PrimaryKey(slug)
}

internal object DealsTable : Table("deals") {
    val id = text("id")
    val skuId = text("sku_id").references(SkusTable.id)
    val priceCents = integer("price_cents")
    val endsAt = timestampWithTimeZone("ends_at")
    override val primaryKey = PrimaryKey(id)
}

/** Every catalog table, parents before children: the order a seed inserts in. */
internal val catalogTables: List<Table> =
    listOf(CategoriesTable, SellersTable, ProductsTable, SkusTable, CampaignsTable, DealsTable)
