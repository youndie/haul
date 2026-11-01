package io.github.youndie.haul.seed

import io.github.youndie.haul.PictureVersions
import io.github.youndie.haul.feature.catalog.data.ProductsTable
import io.github.youndie.haul.feature.catalog.domain.PhotoStore
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.security.MessageDigest

/**
 * Photos for the seed's sample products, drawn here rather than fetched (B-30): the repository ships no
 * photography, and real product photos are the owner's question (B-30 findings). Each is the product's own
 * picture ([SeedArt], B-78) — its category's object in its first SKU's colour over its tile tone — stored as SVG, so
 * a store with object storage shows the headphones as headphones and the mug as a mug (B-149; before it, one tinted
 * box stood for every one of them), sharp at every size the page draws it. Each is stored with its dark version
 * beside it (B-181, [PictureVersions]), so a dark page shows the sample products' photos drawn for it.
 *
 * Only the sample products of research §6 get one, the ones every artboard shows; the generated two
 * thousand keep their pictures, which is the fallback showing itself on the same page.
 */
internal object SeedPhotos {
    private const val KEY_HASH_CHARS = 16
    private const val SVG = "image/svg+xml"
    private const val COLOUR = "colour"

    /**
     * Stores the photo of every sample product that has none — or still has a photo of the seed's that is not
     * today's, such as B-30's tinted box — and records its key; returns how many it stored. Safe to run on every
     * start and from every replica: a product with today's key is skipped, a key that is not the seed's (not
     * under the product's own `products/<id>/`) is left alone, and two replicas racing write the same bytes under
     * the same content-addressed key.
     */
    suspend fun attach(
        database: Database,
        store: PhotoStore,
        products: List<SeedProduct> = SampleCatalog.products,
        skus: List<SeedSku> = SampleCatalog.skus,
    ): Int {
        val photos = products.associate { it.id to draw(it, skus) }
        val darks = products.associate { it.id to draw(it, skus, dark = true) }
        val keys = photos.mapValues { (id, svg) -> keyFor(id, svg, darks.getValue(id)) }
        val held =
            withContext(Dispatchers.IO) {
                transaction(database) {
                    ProductsTable
                        .select(ProductsTable.id, ProductsTable.imageKey)
                        .where { ProductsTable.id inList products.map { it.id } }
                        .associate { it[ProductsTable.id] to it[ProductsTable.imageKey] }
                }
            }
        val todo =
            products.filter { product ->
                val key = held[product.id]
                product.id in held && (key == null || (key != keys[product.id] && seeds(product.id, key)))
            }
        todo.forEach { product ->
            val key = keys.getValue(product.id)
            val was = held[product.id]
            // The dark version first: once the row names the key, a dark page asks for it.
            store.put(PictureVersions.dark(key)!!, darks.getValue(product.id).encodeToByteArray(), SVG)
            store.put(key, photos.getValue(product.id).encodeToByteArray(), SVG)
            withContext(Dispatchers.IO) {
                transaction(database) {
                    ProductsTable.update({
                        (ProductsTable.id eq product.id) and
                            (if (was == null) ProductsTable.imageKey.isNull() else ProductsTable.imageKey eq was)
                    }) {
                        it[imageKey] = key
                    }
                }
            }
        }
        return todo.size
    }

    /** [product]'s photo: its picture in its first SKU's colour, as SVG — for the dark page when [dark]. */
    fun draw(
        product: SeedProduct,
        skus: List<SeedSku> = SampleCatalog.skus,
        dark: Boolean = false,
    ): String {
        val colour =
            skus
                .filter { it.productId == product.id }
                .minByOrNull { it.position }
                ?.options
                ?.get(COLOUR)
                ?.jsonPrimitive
                ?.content
        return SeedArt.productSvg(product.id, product.categorySlug, colour, product.tone, dark)
    }

    /**
     * `products/<id>/<hash>.svg`: the hash of the photo and of its [dark] version in the key, so the photo route may
     * cache both for good.
     */
    fun keyFor(
        productId: String,
        svg: String,
        dark: String,
    ): String {
        val hash =
            MessageDigest
                .getInstance("SHA-256")
                .digest((svg + dark).encodeToByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(KEY_HASH_CHARS)
        return "${ProductPhotos.PREFIX}$productId/$hash.svg"
    }

    /** Whether [key] is one the seed stored for [productId]: a content-addressed photo under its own folder. */
    private fun seeds(
        productId: String,
        key: String,
    ): Boolean {
        val folder = Regex.escape("${ProductPhotos.PREFIX}$productId/")
        return Regex("""$folder[0-9a-f]{$KEY_HASH_CHARS}\.(png|svg)""").matches(key)
    }
}
