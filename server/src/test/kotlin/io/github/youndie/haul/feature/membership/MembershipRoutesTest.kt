package io.github.youndie.haul.feature.membership

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.account.AccountPaths
import io.github.youndie.haul.feature.cart.CartPaths
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.membership.data.ExposedMemberships
import io.github.youndie.haul.feature.membership.domain.PlusMembership
import io.github.youndie.haul.feature.membership.domain.PlusStatus
import io.github.youndie.haul.feature.membership.screen.PlusOffer
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCatalog.STONEWARE_MUG
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.testing.ShildikHarness
import io.github.youndie.haul.testing.assertError
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.ui.AccountBody
import io.github.youndie.haul.ui.AccountTileKind
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.OrderSummary
import io.github.youndie.haul.ui.PlusBlock
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotAction
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.standard.CloseAction
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.PresentAction
import io.github.youndie.kompot.standard.RefreshAction
import io.github.youndie.kompot.standard.SequenceAction
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Haul Plus over HTTP (feature-membership, endpoint-membership), against shildik and a seeded PostgreSQL of
 * each test's own, at the canvas's «now» (Tuesday, Oct 7, 2025): Sam starts the trial and is a member from
 * his next page on, Maya — a member since 2023 — is refused one, and the screens offer it to whom they should.
 */
class MembershipRoutesTest {
    private val maya by lazy {
        ShildikHarness.accessToken(ShildikHarness.person("Maya Kowalski", SampleCustomers.MAYA))
    }
    private val sam by lazy { ShildikHarness.accessToken(ShildikHarness.person("Sam Ortiz", SampleCustomers.SAM)) }

    private fun store(block: suspend HttpClient.(database: DataSource) -> Unit) =
        seededFreshDatabase().use { database -> haulTest(database, signIn = ShildikHarness.signIn) { block(database) } }

    private suspend fun HttpClient.startTrial(token: String?): HttpResponse =
        post(MembershipPaths.TRIAL) { token?.let { bearerAuth(it) } }

    private suspend fun HttpClient.screen(
        path: String,
        token: String?,
    ): KompotComponent {
        val response = get(path) { token?.let { bearerAuth(it) } }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return haulWireJson.decodeKompotComponent(response.bodyAsText())
    }

    private suspend fun HttpClient.delivery(token: String): String =
        screen(CartPaths.SCREEN, token)
            .only<OrderSummary>()
            .rows
            .single { it.label == "Delivery" }
            .value

    /**
     * Scenario «Trial»: Sam, no member, starts the trial on 2025-10-07 — `201`, the dialog closed and the
     * page drawn again — and is on a trial until 2025-11-06; the mug set he puts in his cart, $24 and under
     * the free-delivery threshold, cost $5.99 to deliver before and nothing after. His account now draws the
     * membership with its renewal in place of the offer.
     */
    @Test
    fun `Sam starts the trial and his next cart has free delivery`() =
        store { database ->
            val put =
                put(CartPaths.line("$STONEWARE_MUG-0")) {
                    bearerAuth(sam)
                    contentType(ContentType.Application.Json)
                    setBody(haulWireJson.encodeToString(LineChange.serializer(), LineChange(quantity = 1)))
                }
            assertEquals(HttpStatusCode.OK, put.status, put.bodyAsText())
            assertEquals("$5.99", delivery(sam))

            val started = startTrial(sam)
            assertEquals(HttpStatusCode.Created, started.status, started.bodyAsText())
            assertEquals(
                SequenceAction(listOf(CloseAction, RefreshAction)),
                haulWireJson.decodeKompotAction(started.bodyAsText()),
            )

            val membership =
                runBlocking { ExposedMemberships(Databases.connect(database)).membership(SampleCustomers.SAM) }
            assertEquals(LocalDate.parse("2025-11-06"), membership?.paidFrom)
            assertEquals(PlusStatus.Trial, membership?.status(LocalDate.parse("2025-11-05")))
            assertEquals(PlusStatus.Active, membership?.status(LocalDate.parse("2025-11-06")))
            val again = PlusMembership.trial(SampleCustomers.SAM, CatalogSeed.NOW, LocalDate.parse("2025-10-07"))
            assertFalse(
                runBlocking { ExposedMemberships(Databases.connect(database)).start(again) },
                "a member's second trial is refused by the store as well",
            )
            assertEquals("Free", delivery(sam))

            val tiles = screen(AccountPaths.SCREEN, sam).only<AccountBody>().tiles
            val plus = tiles.single { it.kind == AccountTileKind.Plus }
            assertEquals("Saved on delivery this year · renews Nov 6", plus.text)
            assertTrue(tiles.none { it.kind == AccountTileKind.PlusOffer })
        }

    /**
     * Scenario «Already a member»: Maya, a member since 2023, is refused a trial with `409 already_member`,
     * and so is Sam's second one. Nobody signed in is not let in at all.
     */
    @Test
    fun `a member asking for the trial is refused as already a member`() =
        store {
            startTrial(maya).assertError(HttpStatusCode.Conflict, ErrorCode.AlreadyMember)
            assertEquals(HttpStatusCode.Created, startTrial(sam).status)
            startTrial(sam).assertError(HttpStatusCode.Conflict, ErrorCode.AlreadyMember)
            startTrial(null).assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
        }

    /**
     * Where the trial is offered (screen-home, screen-account; research D6: a member never sees the
     * upsell): «Try 30 days free» presents the dialog to Sam on the home page and on his account, and asks a
     * guest to sign in; Maya's home page draws what Plus saved her this year and when it renews instead.
     */
    @Test
    fun `the trial is offered to a non-member and the savings shown to a member`() =
        store {
            val dialog = PresentAction(PlusOffer.dialog, PlusOffer.DIALOG)
            val sams = screen("/ui/home", sam).only<PlusBlock>()
            assertFalse(sams.member)
            assertEquals("Try 30 days free", sams.offer)
            assertEquals(dialog, sams.action)
            assertEquals(MembershipPaths.TRIAL, PlusOffer.dialog.url)

            val offer =
                screen(AccountPaths.SCREEN, sam).only<AccountBody>().tiles.single {
                    it.kind ==
                        AccountTileKind.PlusOffer
                }
            assertEquals(dialog, offer.button?.action)

            assertEquals(NavigateAction(Frame.SIGN_IN), screen("/ui/home", null).only<PlusBlock>().action)

            val mayas = screen("/ui/home", maya).only<PlusBlock>()
            assertTrue(mayas.member)
            assertEquals("You saved $186 on delivery this year", mayas.title)
            assertEquals("Renews Nov 2", mayas.renewal)
            assertEquals(null, mayas.offer)
        }

    /**
     * B-49: the header's «HAUL PLUS» pill carried nothing. It is the Plus offer as the home page makes it,
     * on every page with the header: the trial's dialog for Sam, no member — the very `present` the Plus
     * block carries — and, once his trial has started, his account, where the membership is drawn, as it is
     * for Maya. A guest's is sign-in (`DrawnActionsTest`).
     */
    @Test
    fun `the plus pill offers the trial to a non-member and the account to a member`() =
        store {
            val dialog = PresentAction(PlusOffer.dialog, PlusOffer.DIALOG)
            listOf("/ui/home", "/ui/c/headphones", CartPaths.SCREEN).forEach { path ->
                assertEquals(dialog, screen(path, sam).only<HaulHeader>().plus, "Sam on $path")
                assertEquals(NavigateAction(Frame.ACCOUNT), screen(path, maya).only<HaulHeader>().plus, "Maya on $path")
            }
            assertEquals(
                screen("/ui/home", sam).only<PlusBlock>().action,
                screen("/ui/home", sam).only<HaulHeader>().plus,
            )

            assertEquals(HttpStatusCode.Created, startTrial(sam).status)
            assertEquals(NavigateAction(Frame.ACCOUNT), screen("/ui/home", sam).only<HaulHeader>().plus)
            val tiles = screen(AccountPaths.SCREEN, sam).only<AccountBody>().tiles
            assertTrue(tiles.any { it.kind == AccountTileKind.Plus }, "the account the pill opens draws no membership")
        }
}
