package io.github.youndie.haul.seed

import io.github.youndie.haul.feature.catalog.data.ProductsTable
import io.github.youndie.haul.feature.catalog.domain.PhotoStore
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.imageio.ImageIO

/**
 * Photos for the seed's sample products, drawn here rather than fetched (B-30): the repository ships no
 * photography, and real product photos are the owner's question (B-30 findings). Each is a still life
 * in the product's tile tone — a gradient, an object, its shadow — so a stored photo is told from the
 * placeholder tile at a glance, and nothing about it depends on a font being installed in the image.
 *
 * Only the sample products of research §6 get one, the ones every artboard shows; the generated two
 * thousand keep their placeholder tiles, which is the fallback showing itself on the same page.
 */
internal object SeedPhotos {
    private const val SIZE = 640
    private const val KEY_HASH_CHARS = 16

    /**
     * Stores a photo for every sample product that has none and records its key; returns how many it
     * stored. Safe to run on every start and from every replica: a product with a key is skipped, and
     * two replicas racing write the same bytes under the same content-addressed key.
     */
    suspend fun attach(
        database: Database,
        store: PhotoStore,
        products: List<SeedProduct> = SampleCatalog.products,
    ): Int {
        val missing =
            withContext(Dispatchers.IO) {
                transaction(database) {
                    ProductsTable
                        .select(ProductsTable.id)
                        .where { (ProductsTable.id inList products.map { it.id }) and ProductsTable.imageKey.isNull() }
                        .map { it[ProductsTable.id] }
                        .toSet()
                }
            }
        val todo = products.filter { it.id in missing }
        todo.forEach { product ->
            val png = draw(product.tone)
            val key = keyFor(product.id, png)
            store.put(key, png, "image/png")
            withContext(Dispatchers.IO) {
                transaction(database) {
                    ProductsTable.update({ (ProductsTable.id eq product.id) and ProductsTable.imageKey.isNull() }) {
                        it[imageKey] = key
                    }
                }
            }
        }
        return todo.size
    }

    /** `products/<id>/<hash>.png`: the content's hash in the key, so the photo route may cache for good. */
    fun keyFor(
        productId: String,
        png: ByteArray,
    ): String {
        val hash =
            MessageDigest
                .getInstance("SHA-256")
                .digest(png)
                .joinToString("") { "%02x".format(it) }
                .take(KEY_HASH_CHARS)
        return "${ProductPhotos.PREFIX}$productId/$hash.png"
    }

    /** A [SIZE]-square PNG in [tone] (`#RRGGBB`): a lit backdrop, an object and its shadow. */
    fun draw(tone: String): ByteArray {
        val base = Color(tone.removePrefix("#").toIntOrNull(HEX) ?: STONE)
        val image = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.paint = GradientPaint(0f, 0f, lighter(base), SIZE.toFloat(), SIZE.toFloat(), darker(base, SHADE_BACK))
            g.fillRect(0, 0, SIZE, SIZE)

            val c = SIZE / 2.0
            g.color = darker(base, SHADE_SHADOW)
            g.fill(Ellipse2D.Double(c - 190, c + 150, 380.0, 70.0))

            val body = RoundRectangle2D.Double(c - 150, c - 170, 300.0, 330.0, 120.0, 120.0)
            g.paint =
                GradientPaint(
                    0f,
                    (c - 170).toFloat(),
                    darker(base, SHADE_TOP),
                    0f,
                    (c + 160).toFloat(),
                    darker(base, SHADE_BOTTOM),
                )
            g.fill(body)
            g.color = lighter(lighter(base))
            g.stroke = BasicStroke(STROKE)
            g.draw(RoundRectangle2D.Double(c - 110, c - 130, 120.0, 180.0, 60.0, 60.0))
        } finally {
            g.dispose()
        }
        return ByteArrayOutputStream().use { out ->
            check(ImageIO.write(image, "png", out)) { "no PNG writer in this runtime" }
            out.toByteArray()
        }
    }

    private const val HEX = 16
    private const val STONE = 0xECE9E2
    private const val SHADE_BACK = 0.92f
    private const val SHADE_SHADOW = 0.72f
    private const val SHADE_TOP = 0.62f
    private const val SHADE_BOTTOM = 0.42f
    private const val STROKE = 6f
    private const val CHANNEL = 255

    private fun darker(
        color: Color,
        by: Float,
    ): Color = Color((color.red * by).toInt(), (color.green * by).toInt(), (color.blue * by).toInt())

    private fun lighter(color: Color): Color =
        Color((color.red + CHANNEL) / 2, (color.green + CHANNEL) / 2, (color.blue + CHANNEL) / 2)
}
