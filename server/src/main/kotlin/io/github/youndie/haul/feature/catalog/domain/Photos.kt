package io.github.youndie.haul.feature.catalog.domain

/** An object read back from the photo store: its bytes and the media type it was stored with. */
internal class StoredPhoto(
    val bytes: ByteArray,
    val contentType: String,
)

/**
 * Where product photos are kept (research D8, B-30): a bucket in an S3-compatible object storage. The
 * port has the two verbs the store needs — the seed writes, the photo route reads — and nothing about
 * buckets or signatures.
 */
internal interface PhotoStore {
    suspend fun put(
        key: String,
        bytes: ByteArray,
        contentType: String,
    )

    /** The object under [key], or `null` when there is none. A store that cannot answer throws. */
    suspend fun get(key: String): StoredPhoto?
}

/**
 * The product photos as the screens and the photo route see them. [store] is `null` when the server
 * has no object storage configured: then no product has a photo, whatever its row says, and every tile
 * is the placeholder — a key left behind in the database must not become an address that cannot load.
 */
internal class ProductPhotos(
    private val store: PhotoStore?,
) {
    /** Where [product]'s photo is served, relative to the server; `null` draws the placeholder tile. */
    fun url(product: Product): String? = product.imageKey?.takeIf { store != null && servable(it) }?.let { "$PATH/$it" }

    /** The photo under [key], or `null` when there is no store, the key is not a photo's, or nothing is there. */
    suspend fun read(key: String): StoredPhoto? = store?.takeIf { servable(key) }?.get(key)

    companion object {
        /** The route the photos are served under. */
        const val PATH = "/images"

        /** The prefix every product photo is stored under; the route serves nothing outside it. */
        const val PREFIX = "products/"

        private val KEY = Regex("""[a-z0-9][a-z0-9._-]*(/[a-z0-9][a-z0-9._-]*)*""")

        /**
         * A key the route may serve: under [PREFIX], lower-case segments, no `..` and no empty segment.
         * The route is public and the bucket is not, so the route is no general window onto the bucket.
         */
        fun servable(key: String): Boolean = key.startsWith(PREFIX) && KEY.matches(key) && ".." !in key
    }
}
