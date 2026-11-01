package io.github.youndie.haul.seed

import io.github.youndie.haul.PictureVersions
import io.github.youndie.haul.feature.catalog.domain.Campaign
import io.github.youndie.haul.feature.catalog.domain.Category
import io.github.youndie.haul.feature.catalog.domain.Illustrations
import io.github.youndie.haul.feature.catalog.domain.Product
import io.github.youndie.haul.seed.SeedMotifs.leaf
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * The seed's own illustrations (B-78, the owner's decision of 2026-10-12): flat SVG pictures in the canvas's style,
 * drawn here from the category, the colour and the tone, and served by the server from its own origin — no licence,
 * no third-party host (B-128), and no object storage, which the stand runs without.
 *
 * - **A product** is drawn as its category's object ([SeedMotifs]: a leaf of the sample catalog by its own, every
 *   other category by its top level's), in its SKU's colour, over its tile tone, in one of three compositions
 *   chosen by its id — so the products of one category share a picture set and differ by colour and composition.
 * - **A category's tile** is its object, plain, over the category's tone.
 * - **A shopper's photo** in a review is the reviewed product's object, plain, over the photo's tone, framed by
 *   another of the three compositions than the product's own picture (B-149).
 * - **The hero** of the Autumn mega sale is falling leaves over its Cobalt; **Tech week's banner** a laptop over its
 *   tone — the two places the canvas keeps for a picture (`Home_Content`'s «CAMPAIGN IMAGE» and «LAPTOP»). Free
 *   delivery weekend's banner has no picture place on the canvas, and draws none.
 *
 * Every picture is drawn twice (B-181): for the light page, and for the dark one — served beside it under the name's
 * dark version ([PictureVersions]), `p_headphones_1_silver_e6e4ff.3f1c9a0e5b.dark.svg`. The dark one is the same
 * art over the tone's dark counterpart, the one the client's dark scheme draws the tile in («Decided in B-180»): the
 * light shape behind the object, the hatching and the marks in the dark page's Paper, the shadows deeper. A picture
 * over a tone the dark page keeps as sent — the hero's Cobalt, a category's Acid — is the same in both.
 *
 * A picture's name is what it is drawn from plus a hash of both its drawings:
 * `p_headphones_1_silver_e6e4ff.3f1c9a0e5b.svg`. The route draws a name again and serves it only when the hash still
 * matches, so a name — and its dark version — always means the same bytes and may be cached for good, and a name from
 * before a change of either drawing answers 404 — the client's tile stays — instead of another picture.
 */
internal object SeedArt : Illustrations {
    private const val SQUARE = 240
    private const val HERO_WIDTH = 360
    private const val HERO_HEIGHT = 560
    private const val HASH_CHARS = 10
    private const val VARIANTS = 3
    private const val PLAIN = "plain"

    /** The tone a picture falls back to when a row's is not a colour: the canvas's last tile tone. */
    private const val STONE = "ece9e2"

    /** The colours the seed's SKUs name (`options.colour`), as drawn; any other colour draws the motif's own. */
    private val SWATCHES =
        mapOf(
            "black" to "#1C1C1F",
            "midnight-black" to "#232329",
            "silver" to "#C8C9CE",
            "beige" to "#DCCBB0",
            "oat" to "#D8C9AC",
            "sage" to "#9DB59A",
            "graphite" to "#4A4B52",
            "midnight" to "#23284A",
            "sand" to "#E1C79B",
            "cobalt" to "#2F2BFF",
        )

    /** The campaigns with a picture: their hero's, and their banner's object. */
    private val HEROES = setOf(SampleCatalog.AUTUMN_MEGA_SALE)
    private val BANNERS = mapOf("tech-week" to "computers")

    /** Each seeded category's parent: the art walks up to the nearest category with an object. */
    private val parents: Map<String, String?> by lazy {
        CatalogSeed.categories().associate { it.slug to it.parentSlug }
    }

    private val names = ConcurrentHashMap<Picture, String>()

    override fun product(
        product: Product,
        colour: String?,
    ): String = name(productPicture(product.id, product.categorySlug, colour, product.tone))

    override fun reviewPhoto(
        product: Product,
        tone: String,
        index: Int,
    ): String =
        name(
            Picture.ProductPicture(
                motifOf(product.categorySlug).slug,
                // A shopper's photo is framed otherwise than the product's own picture, and each of theirs otherwise
                // than the one before.
                Math.floorMod(product.id.hashCode() + index + 1, VARIANTS),
                PLAIN,
                toneOf(tone),
            ),
        )

    /**
     * The product [id] of [categorySlug] in [colour] over [tone], drawn for the dark page when [dark]: the picture
     * [product] names, as SVG. The seed stores it as a sample product's photo when there is object storage (B-149),
     * so the sample products are drawn as their own kind there too — in both schemes (B-181).
     */
    fun productSvg(
        id: String,
        categorySlug: String,
        colour: String?,
        tone: String,
        dark: Boolean = false,
    ): String = productPicture(id, categorySlug, colour, tone).draw(dark)

    private fun productPicture(
        id: String,
        categorySlug: String,
        colour: String?,
        tone: String,
    ): Picture =
        Picture.ProductPicture(
            motifOf(categorySlug).slug,
            Math.floorMod(id.hashCode(), VARIANTS),
            colour?.let(::slug)?.takeIf { it in SWATCHES } ?: PLAIN,
            toneOf(tone),
        )

    override fun category(category: Category): String =
        name(Picture.Tile(motifOf(category.slug).slug, toneOf(category.tone)))

    override fun hero(campaign: Campaign): String? =
        campaign.slug.takeIf { it in HEROES }?.let { name(Picture.Hero(it, toneOf(campaign.tone))) }

    override fun banner(campaign: Campaign): String? =
        BANNERS[campaign.slug]?.let { name(Picture.Tile(it, toneOf(campaign.tone))) }

    override fun svg(name: String): String? {
        val light = PictureVersions.light(name)
        val picture = Picture.parse((light ?: name).substringBefore('.')) ?: return null
        return picture.takeIf { (light ?: name) == nameOf(it) }?.draw(dark = light != null)
    }

    /**
     * The name the picture [name] was drawn from goes by today — [name] itself unless its drawing changed; the dark
     * version's name for a dark version's.
     */
    fun current(name: String): String? {
        val light = PictureVersions.light(name)
        val now = Picture.parse((light ?: name).substringBefore('.'))?.let(::nameOf) ?: return null
        return if (light == null) now else PictureVersions.dark(now)
    }

    /** The object [slug]'s products are drawn with: its own, its nearest ancestor's, or the parcel. */
    fun motifOf(slug: String): Motif =
        generateSequence(slug) { parents[it] }
            .firstNotNullOfOrNull { SeedMotifs.byCategory[it] }
            ?: SeedMotifs.PARCEL

    /**
     * [picture]'s name, kept once worked out. Only the names the screens hand out are kept — they come from the
     * catalogue's rows, a bounded set — never one a request asks for, which could be any tone of sixteen million.
     */
    private fun name(picture: Picture): String = names.getOrPut(picture) { nameOf(picture) }

    /** [picture]'s name: its key and the hash of both its drawings, so neither changes under the name. */
    private fun nameOf(picture: Picture): String = "${picture.key}.${hash(picture.draw(false) + picture.draw(true))}.svg"

    private fun hash(svg: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(svg.encodeToByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(HASH_CHARS)

    private fun slug(colour: String): String =
        colour
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

    private fun toneOf(tone: String): String = tone.removePrefix("#").lowercase().takeIf { TONE.matches(it) } ?: STONE

    private val TONE = Regex("[0-9a-f]{6}")

    /** What a picture is drawn from: its name's part before the hash, and back. */
    private sealed interface Picture {
        val key: String

        /** The picture as SVG, for the dark page when [dark]. */
        fun draw(dark: Boolean): String

        data class ProductPicture(
            val motif: String,
            val variant: Int,
            val colour: String,
            val tone: String,
        ) : Picture {
            override val key = "p_${motif}_${variant}_${colour}_$tone"

            override fun draw(dark: Boolean): String =
                square(SeedMotifs.bySlug.getValue(motif), variant, SWATCHES[colour], Ground.of(tone, dark))
        }

        data class Tile(
            val motif: String,
            val tone: String,
        ) : Picture {
            override val key = "c_${motif}_$tone"

            override fun draw(dark: Boolean): String = square(SeedMotifs.bySlug.getValue(motif), 0, null, Ground.of(tone, dark))
        }

        data class Hero(
            val campaign: String,
            val tone: String,
        ) : Picture {
            override val key = "h_${campaign}_$tone"

            override fun draw(dark: Boolean): String = autumn(tone)
        }

        companion object {
            /** The picture [key] names, or `null` when it names none the seed draws. */
            fun parse(key: String): Picture? {
                val parts = key.split('_')
                val tone = parts.last().takeIf { TONE.matches(it) } ?: return null
                return when {
                    parts.size == 5 && parts[0] == "p" -> {
                        val variant = parts[2].toIntOrNull()?.takeIf { it in 0 until VARIANTS }
                        val colour = parts[3].takeIf { it == PLAIN || it in SWATCHES }
                        if (parts[1] in SeedMotifs.bySlug && variant != null && colour != null) {
                            ProductPicture(parts[1], variant, colour, tone)
                        } else {
                            null
                        }
                    }

                    parts.size == 3 && parts[0] == "c" -> {
                        parts[1].takeIf { it in SeedMotifs.bySlug }?.let { Tile(it, tone) }
                    }

                    parts.size == 3 && parts[0] == "h" -> {
                        parts[1].takeIf { it in HEROES }?.let { Hero(it, tone) }
                    }

                    else -> {
                        null
                    }
                }
            }
        }
    }

    /**
     * A square picture: the [ground] with the canvas's hatching, a light shape behind the object — a disc, an arch,
     * or a band the object leans over, by [variant] — the object's shadow, and the object.
     */
    @Suppress("MagicNumber")
    private fun square(
        motif: Motif,
        variant: Int,
        colour: String?,
        ground: Ground,
    ): String {
        val svg = Svg(SQUARE, SQUARE)
        val body = colour ?: motif.plain
        val paint = paint(motif, body)
        val (back, backOpacity) = ground.back(body)
        svg.rect(0, 0, SQUARE, SQUARE, ground.tone)
        svg.hatching(ground.mark, ground.hatching, 12)
        when (variant) {
            0 -> {
                svg.circle(120, 112, 80, back, opacity = backOpacity)
                svg.ellipse(120, 204, 68, 8, ground.shadow, opacity = ground.shadowOpacity)
                svg.group(30, 22, scale = 1.8) { motif.draw(this, paint) }
            }

            1 -> {
                svg.path("M56,216 L56,100 A64,64 0 0 1 184,100 L184,216 Z", back, opacity = backOpacity)
                svg.circle(198, 58, 7, paint.accent)
                svg.circle(40, 74, 4, ground.mark, opacity = 0.35)
                svg.group(202, 176, degrees = 24) { rect(-6, -6, 12, 12, ground.mark, opacity = 0.12) }
                svg.ellipse(120, 204, 58, 7, ground.shadow, opacity = ground.shadowOpacity)
                svg.group(42, 44, scale = 1.56) { motif.draw(this, paint) }
            }

            else -> {
                svg.path("M0,176 L240,104 L240,240 L0,240 Z", back, opacity = backOpacity)
                svg.ellipse(124, 206, 64, 8, ground.shadow, opacity = ground.shadowOpacity)
                svg.group(120, 116, degrees = -8) { group(-84, -90, scale = 1.68) { motif.draw(this, paint) } }
            }
        }
        return svg.toString()
    }

    /**
     * What a square picture's ground is drawn in: the [tone], the hatching and the small marks in [mark], the
     * object's shadow, and the light shape behind the object ([back]). On the light page it is the canvas's —
     * the tone as sent, Ink's hatching, a white shape. On the dark page (B-181) it is the tone's dark counterpart,
     * the one the client's dark scheme draws the tile in, with the dark page's Paper where the light page has Ink or
     * white, and deeper shadows; a tone the dark page keeps as sent keeps the light page's ground with it.
     */
    private class Ground(
        val tone: String,
        val dark: Boolean,
    ) {
        val mark = if (dark) PAPER else Paint.INK
        val hatching = if (dark) DARK_HATCHING else LIGHT_HATCHING
        val shadow = if (dark) SHADOW else Paint.INK
        val shadowOpacity = if (dark) DARK_SHADOW else LIGHT_SHADOW

        /**
         * The light shape behind an object of [body] colour, and its opacity. On the light page white, or — behind a
         * white object, which it would swallow — the tone a shade darker. On the dark page the Paper, stronger behind a
         * dark object, which the dark tone would swallow.
         */
        fun back(body: String): Pair<String, Double> =
            when {
                dark -> PAPER to if (luminance(body) < DARK) DARK_BACK_UNDER_DARK else DARK_BACK
                luminance(body) > PALE -> mix(tone, Paint.INK, 0.07) to 1.0
                else -> Paint.WHITE to 0.55
            }

        companion object {
            /** [tone] (`rrggbb`) as the page of the scheme draws its tile: its dark counterpart when [dark] and it has one. */
            fun of(
                tone: String,
                dark: Boolean,
            ): Ground {
                val counterpart = DARK_TONES[tone].takeIf { dark }
                return Ground(counterpart ?: "#$tone", counterpart != null)
            }
        }
    }

    /**
     * The canvas's tile tones and the colour the client's dark scheme draws each in («Decided in B-180»,
     * `HaulColorScheme.Dark`): the dark pictures' grounds, so a picture is its tile on the dark page as on the light
     * one. `ArtFixturesTest` and the client's `DarkArtTest` hold the two tables together. `e9e6e0` is the light page's
     * raised surface, a review photo's tone, drawn as the dark page's.
     */
    private val DARK_TONES =
        mapOf(
            "e6e4ff" to "#1D1B39",
            "f1ebdd" to "#302D24",
            "ffe5dd" to "#39221B",
            "f1e4f5" to "#2E2331",
            "fff1c9" to "#39311B",
            "e3f5d8" to "#283321",
            "e9f0d2" to "#2E3223",
            "e0eef7" to "#212C33",
            "ece9e2" to "#2D2B27",
            "e9e6e0" to "#2A2824",
        )

    /** The dark page's Paper: its hatching, its marks and the light shape behind an object. */
    private const val PAPER = "#F5F3EE"
    private const val SHADOW = "#000000"
    private const val LIGHT_HATCHING = 0.05
    private const val DARK_HATCHING = 0.06
    private const val LIGHT_SHADOW = 0.08
    private const val DARK_SHADOW = 0.32
    private const val DARK_BACK = 0.08
    private const val DARK_BACK_UNDER_DARK = 0.16

    /**
     * The Autumn mega sale's hero: leaves falling across its Cobalt, under the hero's hatching. The hero writes its
     * figure in Acid across the picture, so the leaves keep to Cobalt's own shades, white and the canvas's Hot.
     */
    @Suppress("MagicNumber")
    private fun autumn(tone: String): String {
        val svg = Svg(HERO_WIDTH, HERO_HEIGHT)
        svg.rect(0, 0, HERO_WIDTH, HERO_HEIGHT, "#$tone")
        svg.circle(250, 230, 150, Paint.WHITE, opacity = 0.07)
        svg.circle(250, 230, 96, Paint.WHITE, opacity = 0.06)
        listOf(
            Leaf(290, 70, 1.4, 30, Paint.HOT, 1.0),
            Leaf(140, 120, 1.0, -40, Paint.WHITE, 0.55),
            Leaf(320, 210, 1.1, 70, DEEP, 1.0),
            Leaf(80, 250, 1.6, 15, DEEP, 1.0),
            Leaf(210, 330, 1.9, -25, Paint.HOT, 1.0),
            Leaf(330, 410, 1.2, 110, Paint.WHITE, 0.55),
            Leaf(110, 430, 1.1, 60, Paint.WHITE, 0.35),
            Leaf(250, 500, 1.4, -70, DEEP, 1.0),
            Leaf(40, 520, 0.9, 20, Paint.HOT, 1.0),
            Leaf(200, 40, 0.8, 80, DEEP, 1.0),
        ).forEach { leaf ->
            svg.group(leaf.x, leaf.y, scale = leaf.scale * LEAF_SCALE, degrees = leaf.degrees) {
                if (leaf.opacity < 1.0) {
                    path("M0,-22 C14,-10 12,12 0,22 C-12,12 -14,-10 0,-22 Z", leaf.colour, opacity = leaf.opacity)
                } else {
                    leaf(leaf.colour)
                }
            }
        }
        svg.hatching(Paint.WHITE, 0.09, 14)
        return svg.toString()
    }

    /** How much larger than the motif's own leaf the hero's are drawn. */
    private const val LEAF_SCALE = 1.5

    /** Cobalt's own deeper shade, for the leaves that sit back. */
    private const val DEEP = "#1D19C9"

    private class Leaf(
        val x: Int,
        val y: Int,
        val scale: Double,
        val degrees: Int,
        val colour: String,
        val opacity: Double,
    )

    /** The object's paint in [body]: a shade that reads against it, a light of it, an accent that is not it. */
    private fun paint(
        motif: Motif,
        body: String,
    ): Paint {
        val dark = luminance(body) < DARK
        val shade = if (dark) mix(body, Paint.WHITE, 0.2) else mix(body, Paint.INK, 0.22)
        val accent =
            if (motif.accent.equals(body, ignoreCase = true)) {
                if (motif.accent == Paint.ACID) Paint.HOT else Paint.ACID
            } else {
                motif.accent
            }
        return Paint(body, shade, mix(body, Paint.WHITE, 0.5), accent)
    }

    private const val DARK = 0.3
    private const val PALE = 0.8

    private fun rgb(hex: String): IntArray =
        hex
            .removePrefix("#")
            .chunked(2)
            .map { it.toInt(16) }
            .toIntArray()

    private fun luminance(hex: String): Double {
        val (r, g, b) = rgb(hex).map { it / 255.0 }
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun mix(
        from: String,
        to: String,
        by: Double,
    ): String {
        val a = rgb(from)
        val b = rgb(to)
        return "#" + (0..2).joinToString("") { "%02X".format(Math.round(a[it] + (b[it] - a[it]) * by).toInt()) }
    }
}
