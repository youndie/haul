package io.github.youndie.haul.shell

import io.github.youndie.haul.feature.cart.CartPaths
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.cart.LineCommand
import io.github.youndie.haul.feature.catalog.screen.LineAnswers
import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.checkout.CheckoutPaths
import io.github.youndie.haul.feature.identity.GUEST_HEADER
import io.github.youndie.haul.feature.order.OrderPaths
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.testing.FulfilmentWorld
import io.github.youndie.haul.testing.ShildikHarness
import io.github.youndie.haul.testing.action
import io.github.youndie.haul.testing.after
import io.github.youndie.haul.testing.answer
import io.github.youndie.haul.testing.guest
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.liveTree
import io.github.youndie.haul.testing.loads
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.commands.UpdateAction
import io.github.youndie.kompot.decodeKompotComponent
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-65: kompot addresses a node by its `id` — the update channel, the override store, an `update` (B-63) —
 * so an id names one node of a screen's tree (kompot SPEC §4.2); with two, which one an update replaces is
 * undefined. Every page the storefront serves is drawn here, as a guest and as a signed-in customer, and so
 * is every `update` it answers with — the nodes it sends and the page drawn after them — and each tree is
 * walked for an id that appears twice.
 *
 * The walk is over the decoded tree by reflection ([nodesOf]), not a list of the slots each component has:
 * a component that holds another in a slot nobody listed is walked as well. A tree an action presents over
 * the screen (`present`) is a tree of its own and is checked on its own.
 */
class UniqueIdsTest {
    private val maya by lazy {
        ShildikHarness.accessToken(ShildikHarness.person("Maya Kowalski", SampleCustomers.MAYA))
    }

    /** Who is asking: a guest's header or a customer's token on every request. */
    private class Who(
        val sign: HttpRequestBuilder.() -> Unit,
    )

    /** Every id drawn more than once, by the tree it was drawn in; checked once at the end of a test. */
    private class Findings {
        private val found = mutableListOf<String>()
        private val checked = mutableSetOf<KompotComponent>()
        var trees = 0
            private set

        /** The trees [roots] make together, as one screen draws them, and every tree they present. */
        fun check(
            name: String,
            roots: List<KompotComponent>,
        ) {
            trees++
            val seen = Walk().apply { roots.forEach { walk(it, inAction = false) } }
            seen.nodes
                .groupingBy { it.id }
                .eachCount()
                .filterValues { it > 1 }
                .forEach { (id, count) -> found += "$name: «$id» ×$count" }
            seen.presented.filter(checked::add).forEach { check("$name › presented «${it.id}»", listOf(it)) }
        }

        fun assertNone() = assertEquals(emptyList(), found, "ids drawn more than once in one tree")
    }

    /** What a walk over decoded trees met: the nodes in order, the trees actions present, the line commands. */
    private class Walk {
        val nodes = mutableListOf<KompotComponent>()
        val presented = linkedSetOf<KompotComponent>()
        val commands = mutableListOf<LineCommand>()

        fun walk(
            value: Any?,
            inAction: Boolean,
        ) {
            when (value) {
                null, is String, is Number, is Boolean, is Char, is Enum<*> -> {}

                is KompotComponent -> {
                    if (inAction) {
                        presented += value
                    } else {
                        nodes += value
                        fieldsOf(value).forEach { walk(it, inAction = false) }
                    }
                }

                is KompotAction -> {
                    fieldsOf(value).forEach { walk(it, inAction = true) }
                }

                is Iterable<*> -> {
                    value.forEach { walk(it, inAction) }
                }

                is Map<*, *> -> {
                    value.values.forEach { walk(it, inAction) }
                }

                is Array<*> -> {
                    value.forEach { walk(it, inAction) }
                }

                else -> {
                    if (value is LineCommand) commands += value
                    fieldsOf(value).forEach { walk(it, inAction) }
                }
            }
        }

        /** The instance fields of [value]'s own classes — kompot's and Haul's; a library's value is a leaf. */
        private fun fieldsOf(value: Any): List<Any?> =
            generateSequence<Class<*>>(value.javaClass) { it.superclass }
                .takeWhile { it.name.startsWith(OWN_PACKAGES) }
                .flatMap { it.declaredFields.asSequence() }
                .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
                .map { field -> field.also { it.trySetAccessible() }.get(value) }
                .toList()
    }

    private suspend fun HttpClient.body(
        path: String,
        who: Who,
    ): String {
        val response = get(path) { who.sign(this) }
        assertEquals(HttpStatusCode.OK, response.status, "$path: ${response.bodyAsText()}")
        return response.bodyAsText()
    }

    private suspend fun HttpClient.page(
        path: String,
        who: Who,
    ): KompotComponent = haulWireJson.decodeKompotComponent(body(path, who))

    /**
     * Draws [path] and checks it, then every `update` it answers with: each `load` the page carries, and the
     * first line command whose answer is an update of the node it was pressed on (B-63), pressed once. Each
     * update is checked twice: its nodes together, and the page drawn after them.
     */
    private suspend fun HttpClient.everything(
        findings: Findings,
        path: String,
        who: Who,
        press: Boolean = true,
    ) {
        val page = page(path, who)
        findings.check(path, listOf(page))
        page.loads().forEach { url ->
            (answer(url) { who.sign(this) } as? UpdateAction)?.let { findings.updated(path, "load $url", page, it) }
        }
        if (!press) return
        val command =
            Walk()
                .apply { walk(page, inAction = false) }
                .commands
                .firstOrNull { "${LineAnswers.ANSWER}=" in it.url } ?: return
        val pressed =
            put(command.url) {
                who.sign(this)
                contentType(ContentType.Application.Json)
                setBody(haulWireJson.encodeToString(LineChange.serializer(), command.change))
            }.action()
        (pressed as? UpdateAction)?.let { findings.updated(path, "«+» ${command.url}", page, it) }
    }

    private fun Findings.updated(
        path: String,
        what: String,
        page: KompotComponent,
        update: UpdateAction,
    ) {
        check("$path › $what: the update", update.updates.map { it.component })
        check(
            "$path › $what: the page after it",
            listOf(haulWireJson.decodeKompotComponent(page.after(update).toString())),
        )
    }

    @Test
    fun `every page a guest opens draws each id once`() =
        haulTest {
            val findings = Findings()
            val guest = guest()
            val who = Who { header(GUEST_HEADER, guest) }
            GUEST_PAGES.forEach { everything(findings, it, who) }
            // The cart empty above, and now with the lines the presses put in it.
            everything(findings, CartPaths.SCREEN, who)
            assertTrue(findings.trees > GUEST_PAGES.size * 2, "only ${findings.trees} trees were checked")
            findings.assertNone()
        }

    @Test
    fun `every page a customer opens draws each id once`() =
        seededFreshDatabase().use { database ->
            val orderId = FulfilmentWorld(database).use { it.place(CheckoutChoice(slotId = "2025-10-08T15")) }
            haulTest(database, signIn = ShildikHarness.signIn) {
                val findings = Findings()
                val who = Who { bearerAuth(maya) }
                // Maya's cart went into the order: empty first, then with the lines the presses put in it.
                everything(findings, CartPaths.SCREEN, who)
                (GUEST_PAGES + CUSTOMER_PAGES).forEach { everything(findings, it, who) }
                everything(findings, CartPaths.SCREEN, who)
                everything(findings, CheckoutPaths.SCREEN, who, press = false)
                val order = OrderPaths.SCREEN.replace("{id}", orderId)
                findings.check(order, listOf(liveTree(body(order, who), "order:$orderId")))
                findings.assertNone()
            }
        }

    private companion object {
        const val OWN_PACKAGES = "io.github.youndie."

        /** Every page a guest opens, with the filters, pages and tabs that draw something the bare page does not. */
        val GUEST_PAGES =
            listOf(
                "/ui/home",
                "/ui/c",
                "/ui/c/electronics",
                "/ui/c/headphones",
                "/ui/c/mugs",
                "/ui/c/electronics?rating=4.0&expand=brand&sort=price-asc&page=2",
                "/ui/c/headphones?price_min=100000",
                "/ui/search?q=everyday",
                "/ui/search?q=zzqxv",
                "/ui/deals",
                "/ui/deals?page=2",
                "/ui/p/${SampleCatalog.SONY_HEADPHONES}",
                "/ui/p/${SampleCatalog.SONY_HEADPHONES}?sku=${SampleCatalog.SONY_HEADPHONES}-1&tab=reviews",
                "/ui/p/${SampleCatalog.SONY_HEADPHONES}?tab=specifications",
                "/ui/p/${SampleCatalog.STONEWARE_MUG}?tab=questions",
                CartPaths.SCREEN,
            )

        /** The pages only a customer has: the account's. */
        val CUSTOMER_PAGES =
            listOf(
                "/ui" + Frame.ACCOUNT,
                "/ui" + Frame.ORDERS,
                "/ui" + Frame.SAVED,
            )
    }
}
