package io.github.youndie.haul.seed

import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The client draws its goldens from wire bodies (`composeApp/src/desktopTest/resources/bodies`), and since B-78 those
 * name the seed's pictures; the screenshot fixtures draw each from `…/resources/art`, so a golden shows the picture a
 * shopper gets. This holds that directory to the server: every picture a body names is there, is what [SeedArt]
 * draws under that name today — a name whose drawing changed answers nothing, and fails here first — and nothing
 * else is there. On a difference, the directory as it should be is written to `build/art-fixtures`.
 */
class ArtFixturesTest {
    private val bodies = File(System.getProperty("haul.clientBodies") ?: error("haul.clientBodies is not set"))
    private val art = File(System.getProperty("haul.clientArt") ?: error("haul.clientArt is not set"))

    @Test
    fun `every picture the bodies name is the server's, and only those are kept`() {
        val prefix = "${ProductPhotos.PATH}/${ProductPhotos.ART}"
        val named =
            bodies
                .listFiles { f -> f.extension == "json" }
                .orEmpty()
                .flatMap { body ->
                    Regex("\"${Regex.escape(prefix)}([^\"]+)\"").findAll(body.readText()).map { it.groupValues[1] }
                }.toSortedSet()
        // Positive control: the bodies do name pictures, so «every one is kept» was looked for.
        check(named.size > 10) { "the bodies name ${named.size} pictures" }
        val expected = named.associateWith { SeedArt.svg(it) }
        val kept = art.listFiles().orEmpty().associate { it.name to it.readText() }
        val problems =
            expected.filterValues { it == null }.keys.map { "$it: no current picture under that name" } +
                expected
                    .filter { (name, svg) ->
                        svg != null && kept[name] != svg
                    }.keys
                    .map { "$it: not kept as drawn" } +
                (kept.keys - named).map { "$it: kept, but no body names it" }
        if (problems.isNotEmpty()) {
            val out = File("build/art-fixtures").apply { deleteRecursively() }.apply { mkdirs() }
            // What each stale name goes by now, `old new` per line, for the bodies; and every picture they will name.
            val renamed = named.associateWith { SeedArt.current(it) }.filter { (old, new) -> new != null && new != old }
            File(out, "renamed.txt").writeText(renamed.entries.joinToString("") { (old, new) -> "$old $new\n" })
            (named - renamed.keys + renamed.values.filterNotNull()).forEach {
                File(
                    out,
                    it,
                ).writeText(SeedArt.svg(it)!!)
            }
        }
        assertEquals(emptyList(), problems, "the client's pictures are not the server's (see build/art-fixtures)")
    }
}
