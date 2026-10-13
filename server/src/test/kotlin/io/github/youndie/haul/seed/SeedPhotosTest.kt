package io.github.youndie.haul.seed

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.catalog.data.ProductsTable
import io.github.youndie.haul.feature.catalog.domain.PhotoStore
import io.github.youndie.haul.feature.catalog.domain.StoredPhoto
import io.github.youndie.haul.testing.seededFreshDatabase
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The sample products' stored photos are their own pictures (B-149), and a store seeded before that — with B-30's
 * one tinted box per product — gets them on its next start, while a photo that is not the seed's stays.
 */
class SeedPhotosTest {
    private val sony = SampleCatalog.SONY_HEADPHONES
    private val mug = SampleCatalog.STONEWARE_MUG
    private val duvet = SampleCatalog.DUVET_COVER

    @Test
    fun `a photo the seed stored before is replaced by today's and one that is not the seed's is kept`() =
        seededFreshDatabase().use { dataSource ->
            val database = Databases.connect(dataSource)
            val store = MemoryStore()
            // B-30's tinted box under the mug, and a photo the owner put under the duvet.
            val tinted = "${PREFIX}$mug/0123456789abcdef.png"
            val owners = "${PREFIX}$duvet/studio-shot.jpg"
            setKey(database, mug, tinted)
            setKey(database, duvet, owners)

            val stored = runBlocking { SeedPhotos.attach(database, store) }

            assertEquals(SampleCatalog.products.size - 1, stored, "every sample product but the duvet")
            val keys = keys(database)
            assertEquals(today(mug), keys[mug], "the tinted box stayed")
            assertEquals(owners, keys[duvet], "the owner's photo was replaced")
            assertEquals(today(sony), keys[sony])
            assertTrue(store.objects.keys.all { it.endsWith(".svg") }, "${store.objects.keys}")
            assertEquals(SeedPhotos.draw(SampleCatalog.products.single { it.id == mug }), store.text(today(mug)))

            // Again, as on every start: nothing left to store.
            assertEquals(0, runBlocking { SeedPhotos.attach(database, store) })
        }

    /** Today's key of the sample product [id]'s photo. */
    private fun today(id: String): String =
        SeedPhotos.keyFor(id, SeedPhotos.draw(SampleCatalog.products.single { it.id == id }))

    private fun setKey(
        database: Database,
        id: String,
        key: String,
    ) = transaction(database) { ProductsTable.update({ ProductsTable.id eq id }) { it[imageKey] = key } }

    private fun keys(database: Database): Map<String, String?> =
        transaction(database) {
            ProductsTable
                .select(ProductsTable.id, ProductsTable.imageKey)
                .where { ProductsTable.id inList SampleCatalog.products.map { it.id } }
                .associate { it[ProductsTable.id] to it[ProductsTable.imageKey] }
        }

    private class MemoryStore : PhotoStore {
        val objects = ConcurrentHashMap<String, StoredPhoto>()

        override suspend fun put(
            key: String,
            bytes: ByteArray,
            contentType: String,
        ) {
            objects[key] = StoredPhoto(bytes, contentType)
        }

        override suspend fun get(key: String): StoredPhoto? = objects[key]

        fun text(key: String): String? = objects[key]?.bytes?.decodeToString()
    }

    private companion object {
        const val PREFIX = "products/"
    }
}
