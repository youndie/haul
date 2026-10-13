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
 * The pictures a product, a category or a campaign is drawn with when no photo is stored for it (B-78): the
 * seed's own illustrations ([io.github.youndie.haul.seed.SeedArt]), drawn by the server and served from its own
 * origin. Each answer is a picture's name under [ProductPhotos.ART]; the name carries the picture's content hash,
 * so a picture never changes under its name and the route may cache it for good.
 */
internal interface Illustrations {
    /** [product]'s picture in [colour] — its SKU's `colour` option, or `null` for the motif's own. */
    fun product(
        product: Product,
        colour: String?,
    ): String

    /**
     * The picture standing for photo [index] of a review of [product], whose placeholder is [tone] (B-149): the
     * product's object, plain, over the photo's tone.
     */
    fun reviewPhoto(
        product: Product,
        tone: String,
        index: Int,
    ): String

    /** [category]'s tile. */
    fun category(category: Category): String

    /** The hero's picture for [campaign], or `null` when there is none for it. */
    fun hero(campaign: Campaign): String?

    /** A banner's picture for [campaign], or `null` when its banner draws none. */
    fun banner(campaign: Campaign): String?

    /** The SVG named [name], or `null` when [name] names no current picture. */
    fun svg(name: String): String?
}

/**
 * The product photos as the screens and the photo route see them. [store] is `null` when the server
 * has no object storage configured: then no product has a stored photo, whatever its row says — a key left
 * behind in the database must not become an address that cannot load — and every product is drawn with its
 * picture from [art] (B-78), as is every product without a stored photo when there is a store. A stored
 * photo always wins.
 */
internal class ProductPhotos(
    private val store: PhotoStore?,
    private val art: Illustrations,
) {
    /**
     * Where [product]'s photo is served, relative to the server: the stored photo when there is one, else its
     * picture in [sku]'s colour (B-78).
     */
    fun url(
        product: Product,
        sku: Sku?,
    ): String = stored(product) ?: "$PATH/$ART${art.product(product, sku?.options?.get(COLOUR))}"

    /** The picture of photo [index] of a review of [product], whose placeholder is [tone] (B-149). */
    fun reviewPhoto(
        product: Product,
        tone: String,
        index: Int,
    ): String = "$PATH/$ART${art.reviewPhoto(product, tone, index)}"

    /** [category]'s tile picture. */
    fun category(category: Category): String = "$PATH/$ART${art.category(category)}"

    /** The hero's picture for [campaign], or `null`: the hero keeps its hatching. */
    fun hero(campaign: Campaign): String? = art.hero(campaign)?.let { "$PATH/$ART$it" }

    /** A banner's picture for [campaign], or `null`: the banner draws no picture. */
    fun banner(campaign: Campaign): String? = art.banner(campaign)?.let { "$PATH/$ART$it" }

    private fun stored(product: Product): String? =
        product.imageKey?.takeIf { store != null && servable(it) }?.let { "$PATH/$it" }

    /**
     * What is under [key]: a stored photo, or a picture when [key] is under [ART]; `null` when there is no store,
     * the key is neither, or nothing is there.
     */
    suspend fun read(key: String): StoredPhoto? =
        if (key.startsWith(ART)) {
            art.svg(key.removePrefix(ART))?.let { StoredPhoto(it.encodeToByteArray(), SVG) }
        } else {
            store?.takeIf { servable(key) }?.get(key)
        }

    companion object {
        /** The route the photos are served under. */
        const val PATH = "/images"

        /** The prefix every product photo is stored under; the route serves nothing outside it but [ART]. */
        const val PREFIX = "products/"

        /** The prefix the pictures are served under (B-78); nothing is stored there. */
        const val ART = "art/"

        /** What the pictures are sent as. */
        const val SVG = "image/svg+xml"

        private const val COLOUR = "colour"

        private val KEY = Regex("""[a-z0-9][a-z0-9._-]*(/[a-z0-9][a-z0-9._-]*)*""")

        /**
         * A key the route may serve: under [PREFIX], lower-case segments, no `..` and no empty segment.
         * The route is public and the bucket is not, so the route is no general window onto the bucket.
         */
        fun servable(key: String): Boolean = key.startsWith(PREFIX) && KEY.matches(key) && ".." !in key
    }
}
