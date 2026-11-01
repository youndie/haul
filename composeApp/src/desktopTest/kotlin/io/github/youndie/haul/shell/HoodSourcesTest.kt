package io.github.youndie.haul.shell

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * B-163: a component's links in «Under the hood» lead to files of the repository — the view that draws its type and
 * the server's file that builds it — and the table names only wire types the contract has. A file moved or renamed
 * fails here, not as a dead link on the stand.
 */
class HoodSourcesTest {
    // The tests run in `composeApp`; the repository is its parent.
    private val repository = File("..")

    private val wireTypes =
        File(repository, "shared/src/commonMain")
            .walk()
            .filter { it.extension == "kt" }
            .flatMap { SERIAL_NAME.findAll(it.readText()).map { match -> match.groupValues[1] } }
            .toSet()

    @Test
    fun `every link of the outline is a file of the repository`() {
        HoodSources.types.forEach { type ->
            val source = HoodSources.of(type)!!
            listOfNotNull(source.view, source.builder).forEach {
                assertTrue(File(repository, it).isFile, "$type links to $it, which is not in the repository")
                assertTrue(HoodSources.url(it).startsWith("https://github.com/youndie/haul/blob/main/"))
            }
        }
    }

    @Test
    fun `the table names only the contract's wire types and every type it names is drawn and built there`() {
        HoodSources.types.forEach { type ->
            assertTrue(type in wireTypes, "$type is no wire type of the contract")
            val source = HoodSources.of(type)!!
            val name = CLASS_OF.getValue(type)
            source.builder?.let {
                assertTrue("$name(" in File(repository, it).readText(), "$it does not build $type ($name)")
            }
        }
    }

    private companion object {
        val SERIAL_NAME = Regex("""@SerialName\("(haul_[a-z_]+)"\)""")

        /** The contract's class for each type the table names a builder for, as the builder writes it. */
        val CLASS_OF: Map<String, String> by lazy {
            File("../shared/src/commonMain")
                .walk()
                .filter { it.extension == "kt" }
                .flatMap { file ->
                    Regex("""@SerialName\("(haul_[a-z_]+)"\)\s*(?:@\w+\s*)*public data class (\w+)""")
                        .findAll(file.readText())
                        .map { it.groupValues[1] to it.groupValues[2] }
                }.toMap()
        }
    }
}
