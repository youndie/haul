package io.github.youndie.haul

import io.github.youndie.haul.feature.identity.Identity
import io.github.youndie.haul.feature.identity.IdentityApi
import io.github.youndie.haul.feature.identity.LoopbackSignInFlow
import io.github.youndie.haul.feature.identity.MemorySessionStore
import io.github.youndie.haul.feature.identity.PreferencesSessionStore
import io.github.youndie.haul.feature.identity.Session
import io.github.youndie.haul.feature.identity.SignInActions
import io.github.youndie.haul.feature.identity.Tokens
import io.github.youndie.haul.shell.BackStackHistory
import io.github.youndie.kompot.standard.NavigateAction
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The desktop app's own history and session (B-176): what a browser keeps for a tab, kept by the app. */
class DesktopPartsTest {
    private val node = Preferences.userRoot().node("io/github/youndie/haul-test-${System.nanoTime()}")

    @AfterTest
    fun forget() = node.removeNode()

    @Test
    fun `back and forward move through the entries and tell the shell where they arrived`() {
        val history = BackStackHistory()
        val arrived = mutableListOf<String>()
        history.listen { arrived += it }
        assertFalse(history.canGoBack)

        history.push("/c")
        history.push("/p/p-1")
        history.back()
        history.back()
        history.back()
        history.forward()
        history.push("/cart")

        assertEquals(listOf("/c", "/", "/c"), arrived, "a back with nowhere to go tells nobody")
        assertEquals("/cart", history.location)
        assertFalse(history.canGoForward, "a new entry drops the ones ahead of it")
        assertTrue(history.canGoBack)
    }

    @Test
    fun `the guest and a sign-in outlive the window, one per server`() {
        val stand = PreferencesSessionStore("https://haul.kotlin.website", node)
        stand.save(Session(guestId = "g-1", tokens = Tokens("access", "refresh")))
        PreferencesSessionStore("http://localhost:8080/", node).save(Session(guestId = "g-local"))

        assertEquals(Session(guestId = "g-1", tokens = Tokens("access", "refresh")), stand.load())
        assertEquals(
            Session(guestId = "g-1", tokens = Tokens("access", "refresh")),
            PreferencesSessionStore("https://haul.kotlin.website/", node).load(),
            "the next window is signed in as this one was (B-178)",
        )
        assertEquals(Session(guestId = "g-local"), PreferencesSessionStore("http://localhost:8080", node).load())

        stand.save(Session(guestId = "g-1"))
        assertEquals(Session(guestId = "g-1"), PreferencesSessionStore("https://haul.kotlin.website", node).load())
        stand.save(Session())
        assertNull(PreferencesSessionStore("https://haul.kotlin.website", node).load().guestId)
    }

    @Test
    fun `tokens longer than a preference holds last the window alone`() {
        val long = Tokens("a".repeat(Preferences.MAX_VALUE_LENGTH), "refresh")
        val stand = PreferencesSessionStore("https://haul.kotlin.website", node)
        stand.save(Session(guestId = "g-1", tokens = long))

        assertEquals(long, stand.load().tokens, "this window keeps them")
        assertEquals(Session(guestId = "g-1"), PreferencesSessionStore("https://haul.kotlin.website", node).load())
    }

    @Test
    fun `a sign-in on a server with sign-in off opens no browser, the guest and the cart kept`() =
        runBlocking {
            val asked = mutableListOf<String>()
            val server =
                MockEngine {
                    asked += it.url.encodedPath
                    respondError(HttpStatusCode.ServiceUnavailable)
                }
            val sessions = MemorySessionStore(Session(guestId = "g-1"))
            val opened = mutableListOf<String>()
            val flow = LoopbackSignInFlow(browse = { opened += it })
            val identity = Identity(IdentityApi(storeClient("https://stand.test", server)), flow, sessions)
            var cancelled = 0
            val actions = SignInActions(signIn = identity::signIn, redraw = {}, cancelled = { cancelled++ }, open = {})

            assertTrue(actions.handle(NavigateAction(SignInActions.returningTo("/cart"))))

            assertEquals(1, cancelled, "the page is drawn again for the guest")
            assertFalse(identity.signedIn)
            assertEquals(Session(guestId = "g-1"), sessions.load())
            assertEquals(listOf("/api/v1/sign-in"), asked, "the server is asked where to sign in, and says nowhere")
            assertEquals(emptyList<String>(), opened, "no browser is opened")
        }
}
