package io.github.youndie.haul.feature.identity

import io.github.youndie.kompot.standard.NavigateAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * B-46: the browser's sign-in ends when its popup does. kotlin-multiplatform-oidc 0.18.4 waits for
 * the return page's message and nothing else — walked in Chrome against a shildik realm, a closed
 * popup left the sign-in pending for good, B-44's «go home» never ran, and the next press opened a
 * second window. These drive [PopupSignInFlow] through [SignInActions] with a fake popup and a fake
 * of the library's wait, which, like the library's, does not give in to cancellation.
 */
class PopupSignInFlowTest {
    private val settings =
        SignInSettings("http://shildik.test/realms/haul", "haul-web", "openid", "/api/v1/me/cart/merge")
    private val popups = mutableListOf<FakePopup>()
    private val library = LibraryFlow()
    private var redraws = 0
    private var cancelled = 0
    private val blocked = mutableListOf<String>()
    private val opened = mutableListOf<String>()

    private fun actions(
        flow: PopupSignInFlow,
        settings: suspend () -> SignInSettings = { this.settings },
    ) = SignInActions(
        signIn = { flow.signIn(settings) },
        redraw = { redraws++ },
        cancelled = { cancelled++ },
        blocked = { blocked += it },
        open = { opened += it },
    )

    private fun flow(pollEvery: Duration = PopupSignInFlow.POLL) =
        PopupSignInFlow(open = { FakePopup().also { popups += it } }, delegate = library, pollEvery = pollEvery)

    private val presses = mutableListOf<Job>()

    /**
     * A press, detached from the test's own coroutine: the library's wait does not give in to
     * cancellation, and a press stuck in it — the defect — would otherwise hold `runBlocking` open.
     */
    private fun CoroutineScope.press(
        actions: SignInActions,
        deeplink: String = "/sign-in?next=%2Fcheckout",
    ): Job =
        CoroutineScope(coroutineContext + Job())
            .launch { actions.handle(NavigateAction(deeplink)) }
            .also { presses += it }

    /** `runBlocking` that lets go of the presses still running when the body ends. */
    private fun walk(body: suspend CoroutineScope.() -> Unit) =
        runBlocking {
            try {
                body()
            } finally {
                presses.forEach { it.cancel() }
            }
        }

    /** Lets the presses run until the library has been asked [times] times; bounded, so a miss fails. */
    private suspend fun awaitStarted(times: Int) {
        repeat(1_000) {
            if (library.started == times) return
            yield()
        }
        error("the provider's flow was started ${library.started} times, not $times")
    }

    /**
     * The defect itself: the shopper closes the popup, and the press must end as «did not go through»
     * — B-44's prompt goes home on it — within a second, at the poll the browser runs.
     */
    @Test
    fun `a popup the shopper closes ends the sign-in as cancelled within a second`() =
        walk {
            val actions = actions(flow())
            val press = press(actions)
            awaitStarted(1)

            popups.single().closed = true
            withTimeout(1.seconds) { press.join() }

            assertEquals(1, cancelled, "the closed popup did not end the sign-in")
            assertEquals(0, redraws)
            assertEquals(emptyList(), opened)

            // Settled, not merely abandoned: the next press opens a popup of its own.
            press(actions)
            awaitStarted(2)
            assertEquals(2, popups.size)
        }

    /**
     * A browser that blocks the popup ends the sign-in at once, and neither the server nor the provider
     * is asked. It is not a sign-in the shopper abandoned (B-66): it is told as blocked, with the press's
     * own address, for the page that says so and offers the sign-in in this tab.
     */
    @Test
    fun `a blocked popup ends the sign-in as blocked without asking the server or the provider`() =
        walk {
            var asked = 0
            val flow = PopupSignInFlow(open = { null }, delegate = library)
            val press = press(actions(flow) { settings.also { asked++ } })

            withTimeout(1.seconds) { press.join() }

            assertEquals(listOf("/sign-in?next=%2Fcheckout"), blocked)
            assertEquals(0, cancelled)
            assertEquals(0, asked, "the server was asked for the settings before the popup")
            assertEquals(0, library.started)
            assertEquals(emptyList(), opened)
        }

    /**
     * B-66: the popup is opened inside the press, before the wait for the server's settings — a browser
     * blocks a popup opened after a wait it saw no click for, and the first press of a page load waits for
     * them. Settings that fail close the popup, and the sign-in did not go through.
     */
    @Test
    fun `the popup opens before the settings are asked for, and closes when they fail`() =
        walk {
            val popupsWhenAsked = mutableListOf<Int>()
            val press =
                press(
                    actions(flow()) {
                        popupsWhenAsked += popups.size
                        throw SignInUnavailable()
                    },
                )

            withTimeout(1.seconds) { press.join() }

            assertEquals(listOf(1), popupsWhenAsked)
            assertTrue(popups.single().closed, "the popup outlived a sign-in that could not start")
            assertEquals(1, cancelled)
            assertEquals(0, library.started)
        }

    /**
     * A second press while the first sign-in is pending brings its popup forward and does nothing
     * else: no second window, and none of the press's own endings — the first press answers for both.
     */
    @Test
    fun `a second press while a sign-in is pending focuses its popup and opens no other`() =
        walk {
            val actions = actions(flow())
            val first = press(actions)
            awaitStarted(1)

            val second = press(actions, deeplink = "/sign-in")
            withTimeout(1.seconds) { second.join() }

            assertEquals(1, popups.size, "a second popup was opened")
            assertEquals(1, popups.single().focused)
            assertEquals(1, library.started)
            assertEquals(0, cancelled + redraws + opened.size, "the second press ended something")

            library.answer(Tokens("access-1"))
            withTimeout(1.seconds) { first.join() }
            assertEquals(listOf("/checkout"), opened, "the first press no longer answered")
        }

    /**
     * The return page posts its answer and closes the window while the code is still being exchanged:
     * that close is the flow's own, and the sign-in goes through.
     */
    @Test
    fun `a popup that answered and closed still signs the shopper in`() =
        walk {
            val press = press(actions(flow(pollEvery = 10.milliseconds)))
            awaitStarted(1)

            popups.single().answered = true
            popups.single().closed = true
            delay(100.milliseconds) // the exchange takes a while, many polls long
            library.answer(Tokens("access-1"))
            withTimeout(1.seconds) { press.join() }

            assertEquals(listOf("/checkout"), opened)
            assertEquals(0, cancelled, "the return page's own close was read as the shopper's")
        }

    /**
     * B-47: in the browser a fetch that fails during the token exchange reaches Kotlin as a JavaScript
     * error, which on Wasm is a `Throwable` and no `Exception`. Caught as an `Exception` only, it escaped
     * the press: nothing was cancelled, B-44's prompt never went home, and the shopper was left on it with
     * nothing happening. [BrowserError] stands in for it on the desktop, where no such error exists.
     */
    @Test
    fun `a token exchange that fails with a browser error ends the sign-in as cancelled`() =
        walk {
            val actions = actions(flow())
            val press = press(actions)
            awaitStarted(1)

            library.fail(BrowserError())
            withTimeout(1.seconds) { press.join() }

            assertEquals(1, cancelled, "the browser's error escaped the press")
            assertEquals(0, redraws)
            assertEquals(emptyList(), opened)
            assertTrue(popups.single().closed, "the popup was left open")

            // Settled: the next press opens a popup of its own.
            press(actions)
            awaitStarted(2)
            assertEquals(2, popups.size)
        }

    /** A press that goes away mid-sign-in (the page left) closes its popup and leaves none pending. */
    @Test
    fun `a sign-in whose press is gone closes its popup`() =
        walk {
            val actions = actions(flow())
            val press = press(actions)
            awaitStarted(1)

            press.cancel()
            withTimeout(1.seconds) { press.join() }

            assertTrue(popups.single().closed, "the popup was left open")
            press(actions)
            awaitStarted(2)
            assertEquals(2, popups.size)
        }

    private class FakePopup : SignInPopup {
        override var closed = false
        override var answered = false
        var focused = 0

        override fun focus() {
            focused++
        }

        override fun close() {
            closed = true
        }
    }

    /**
     * kotlin-multiplatform-oidc's popup flow, as far as B-46 goes: it waits for the return page's
     * answer, which only [answer] gives, in a `suspendCoroutine` that does not give in to cancellation.
     */
    private class LibraryFlow : SignInFlow {
        var started = 0
        private var waiting: Continuation<Tokens>? = null

        override suspend fun signIn(settings: suspend () -> SignInSettings): Tokens {
            settings()
            started++
            return suspendCoroutine { waiting = it }
        }

        fun answer(tokens: Tokens) {
            checkNotNull(waiting) { "nothing is waiting for an answer" }.resume(tokens)
        }

        fun fail(error: Throwable) {
            checkNotNull(waiting) { "nothing is waiting for an answer" }.resumeWithException(error)
        }

        override suspend fun refresh(
            settings: SignInSettings,
            refreshToken: String,
        ): Tokens = error("refresh is not part of a sign-in")
    }

    /** What a failed `fetch` is to Kotlin/Wasm: a `Throwable` that is no `Exception`. */
    private class BrowserError : Throwable("TypeError: Failed to fetch")
}
