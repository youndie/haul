package io.github.youndie.haul.seed

import io.github.youndie.haul.feature.catalog.domain.Category
import io.github.youndie.haul.feature.catalog.domain.Product
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B-78: the seed's own pictures. A product is drawn as its category's object in its SKU's colour over its tone, the
 * products of one category share the set, every name the art hands out answers its SVG, and a name it did not hand
 * out — or one whose drawing has changed since — answers nothing.
 */
class SeedArtTest {
    @Test
    fun `a product is drawn as its category's object, found by walking up the seed's categories`() {
        assertEquals("headphones", SeedArt.motifOf("headphones").slug)
        // A generated leaf, «Fashion › Essentials › Classics», draws its top level's object.
        assertEquals("fashion", SeedArt.motifOf("fashion-essentials-classics").slug)
        assertEquals("kids-toys", SeedArt.motifOf("kids-toys-premium-limited").slug)
        // A category the seed does not know (a store's own) still has a picture.
        assertEquals("parcel", SeedArt.motifOf("somebody-elses-category").slug)
        // Every top-level category of the seed has an object of its own.
        val seeded = CatalogSeed.categories()
        val tops = seeded.filter { it.parentSlug == null }
        assertEquals(emptyList(), tops.filter { SeedArt.motifOf(it.slug) == SeedMotifs.PARCEL }.map { it.slug })
        val leaves = seeded.filter { leaf -> seeded.none { it.parentSlug == leaf.slug } }
        assertEquals(emptyList(), leaves.filter { SeedArt.motifOf(it.slug) == SeedMotifs.PARCEL }.map { it.slug })
    }

    @Test
    fun `one category's products share a set and differ by colour`() {
        val black = SeedArt.product(product("p-1", "headphones"), "Midnight Black")
        val silver = SeedArt.product(product("p-1", "headphones"), "Silver")
        assertTrue(black.startsWith("p_headphones_"), black)
        assertNotEquals(black, silver)
        assertTrue("#232329" in assertNotNull(SeedArt.svg(black)), "the black SKU is not drawn black")
        assertTrue("#C8C9CE" in assertNotNull(SeedArt.svg(silver)), "the silver SKU is not drawn silver")
        // A colour the seed has no swatch for, and none at all, draw the object's own.
        assertEquals(
            SeedArt.product(product("p-1", "headphones"), null),
            SeedArt.product(product("p-1", "headphones"), "Ultraviolet"),
        )
        // The three compositions are all in use among one category's products.
        val variants = (0 until 30).map { SeedArt.product(product("p-$it", "headphones"), null).split('_')[2] }.toSet()
        assertEquals(setOf("0", "1", "2"), variants)
    }

    @Test
    fun `every name answers its picture, and the same one every time`() {
        val names =
            SeedMotifs.byCategory.keys.flatMap { slug ->
                (0 until 6).map {
                    SeedArt.product(
                        product("p-$slug-$it", slug),
                        listOf(null, "Oat", "Midnight")[it % 3],
                    )
                }
            } + SeedArt.category(category("electronics", "#E6E4FF"))
        names.forEach { name ->
            val svg = assertNotNull(SeedArt.svg(name), name)
            assertEquals(svg, SeedArt.svg(name), "drawn twice, $name differs")
            // Well-formed, or the browser draws nothing and the tile stays.
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                ByteArrayInputStream(svg.encodeToByteArray()),
            )
            assertTrue(svg.length < MAX_BYTES, "$name is ${svg.length} bytes")
        }
    }

    @Test
    fun `a name the art did not hand out answers nothing`() {
        val name = SeedArt.product(product("p-1", "mugs"), "Sage")
        assertNotNull(SeedArt.svg(name), "the control name answers nothing")
        val (key, hash) = name.removeSuffix(".svg").split('.')
        assertNull(SeedArt.svg("$key.${hash.reversed()}.svg"), "a changed hash")
        assertNull(SeedArt.svg(name.replace("p_mugs_", "p_spoons_")), "an object the seed has not")
        assertNull(SeedArt.svg(name.replace("_sage_", "_ultraviolet_")), "a colour the seed has not")
        assertNull(SeedArt.svg(name.replace("p_mugs_", "x_mugs_")), "a kind of picture there is not")
        assertNull(SeedArt.svg("../products/nobody/0000000000000000.png"))
    }

    private fun product(
        id: String,
        category: String,
    ) = Product(
        id = id,
        sellerId = "s-01",
        categorySlug = category,
        title = "A product",
        brand = "Brand",
        description = "",
        specifications = emptyList(),
        rating = BigDecimal("4.5"),
        reviewsCount = 1,
        questionsCount = 0,
        tone = "#E6E4FF",
        label = category,
        features = emptyList(),
        kind = null,
        dispatchDays = 0,
        createdAt = CatalogSeed.NOW,
        headline = "A headline",
        headlineAccent = null,
    )

    private fun category(
        slug: String,
        tone: String,
    ) = Category(slug, null, slug, 0, tone, slug)

    private companion object {
        /** A picture is a few kilobytes: a tile's worth of SVG, not a photograph. */
        const val MAX_BYTES = 6_000
    }
}
