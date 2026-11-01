package io.github.youndie.haul

import io.github.youndie.haul.feature.identity.Identity
import io.github.youndie.haul.feature.identity.IdentityApi
import io.github.youndie.haul.feature.identity.MemorySessionStore
import io.github.youndie.haul.feature.identity.NoDesktopSignIn
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
    fun `the guest outlives the window, one per server, and a sign-in does not`() {
        val stand = PreferencesSessionStore("https://haul.kotlin.website", node)
        stand.save(Session(guestId = "g-1", tokens = Tokens("access")))
        PreferencesSessionStore("http://localhost:8080/", node).save(Session(guestId = "g-local"))

        assertEquals(Session(guestId = "g-1", tokens = Tokens("access")), stand.load(), "this window keeps both")
        assertEquals(Session(guestId = "g-1"), PreferencesSessionStore("https://haul.kotlin.website/", node).load())
        assertEquals("g-local", PreferencesSessionStore("http://localhost:8080", node).load().guestId)

        stand.save(Session())
        assertNull(PreferencesSessionStore("https://haul.kotlin.website", node).load().guestId)
    }

    @Test
    fun `a sign-in in the window ends as on a server with sign-in off, the guest and the cart kept`() =
        runBlocking {
            val asked = mutableListOf<String>()
            val server =
                MockEngine {
                    asked += it.url.encodedPath
                    respondError(HttpStatusCode.NotFound)
                }
            val sessions = MemorySessionStore(Session(guestId = "g-1"))
            val identity = Identity(IdentityApi(storeClient("https://stand.test", server)), NoDesktopSignIn, sessions)
            var cancelled = 0
            val actions = SignInActions(signIn = identity::signIn, redraw = {}, cancelled = { cancelled++ }, open = {})

            assertTrue(actions.handle(NavigateAction(SignInActions.returningTo("/cart"))))

            assertEquals(1, cancelled, "the page is drawn again for the guest")
            assertFalse(identity.signedIn)
            assertEquals(Session(guestId = "g-1"), sessions.load())
            assertEquals(emptyList<String>(), asked, "nothing is asked of the server or the provider")
        }
}
