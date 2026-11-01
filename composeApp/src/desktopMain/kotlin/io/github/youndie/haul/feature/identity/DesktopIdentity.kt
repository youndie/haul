package io.github.youndie.haul.feature.identity

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.youndie.haul.registry.haulJson
import io.ktor.http.Url
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import org.publicvalue.multiplatform.oidc.ExperimentalOpenIdConnect
import org.publicvalue.multiplatform.oidc.OpenIdConnectClient
import org.publicvalue.multiplatform.oidc.appsupport.JvmCodeAuthFlowFactory
import org.publicvalue.multiplatform.oidc.appsupport.webserver.Webserver
import java.awt.Desktop
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.prefs.Preferences
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Where the desktop app keeps who it is (B-176, B-178), in the user's preferences, one entry per [origin] — so a local
 * stack and the stand each keep their own: the guest id, as a browser keeps it in `localStorage`, and a signed-in
 * customer's tokens, so a sign-in outlives the window as an installed app's does (a browser keeps them for the tab).
 * Tokens longer than a preference holds ([Preferences.MAX_VALUE_LENGTH]) are kept for the window alone.
 */
internal class PreferencesSessionStore(
    private val origin: String,
    private val node: Preferences = Preferences.userRoot().node(NODE),
) : SessionStore {
    private var tokens: Tokens? = stored()

    override fun load(): Session = Session(guestId = node.get(key(GUEST), null), tokens = tokens)

    override fun save(session: Session) {
        tokens = session.tokens
        put(GUEST, session.guestId)
        val kept = session.tokens?.let { haulJson.encodeToString(Tokens.serializer(), it) }
        put(TOKENS, kept?.takeIf { it.length <= Preferences.MAX_VALUE_LENGTH })
    }

    private fun put(
        what: String,
        value: String?,
    ) = if (value == null) node.remove(key(what)) else node.put(key(what), value)

    @Suppress(
        "ktlint:kapkan:swallowed-failure",
        "Tokens that do not read back — written by another version of the app — are a sign-in that is over: the shopper is the guest they were and sees «Sign in».",
    )
    private fun stored(): Tokens? =
        try {
            node.get(key(TOKENS), null)?.let { haulJson.decodeFromString(Tokens.serializer(), it) }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    // A preference's key is at most 80 characters; an origin longer than that is cut, which only shares a guest
    // between two such servers, and the second one answers `guest_not_found` and is given its own (B-115) — or
    // refuses the other's token, which is renewed or forgotten the same way.
    private fun key(what: String): String = (what + origin.trimEnd('/')).take(Preferences.MAX_KEY_LENGTH)

    private companion object {
        const val NODE = "io/github/youndie/haul"
        const val GUEST = "guest "
        const val TOKENS = "tokens "
    }
}

/**
 * The desktop app's sign-in (B-178): kotlin-multiplatform-oidc's JVM flow — the authorization code with PKCE, the
 * provider's page in the system's browser, the code caught by a listener on a loopback port — against the realm's
 * public client, the browser's own (`haul-web`), which registers `http://127.0.0.1/signed-in` without a port; shildik
 * matches it on any (from 0.5.0), so the port is whichever the system has free at each sign-in ([LoopbackListener]).
 *
 * - [browse] opens the provider's page; a system with no browser to open it in ends the sign-in at once.
 * - A press while a sign-in is under way opens its page again — a tab the shopper closed is not something the app can
 *   see — and throws [SignInPending], so the first press answers when that sign-in ends.
 * - A sign-in nobody finishes in [waitAtMost] ends as one that did not go through: its listener gives up
 *   ([SignInAbandoned], which the library hands on wrapped in its own failure) and is closed.
 * - [refresh] renews any token of the realm's client: a sign-in's here, and a demo shopper's (B-162), which the server
 *   signed in at the same client.
 */
@OptIn(ExperimentalOpenIdConnect::class)
internal class LoopbackSignInFlow(
    private val browse: (String) -> Unit = ::systemBrowser,
    private val waitAtMost: Duration = WAIT,
) : SignInFlow {
    private val pending = AtomicReference<Pending?>()

    private class Pending {
        @Volatile var page: String? = null
    }

    override suspend fun signIn(settings: suspend () -> SignInSettings): Tokens {
        val mine = Pending()
        if (!pending.compareAndSet(null, mine)) {
            pending.get()?.page?.let(browse)
            throw SignInPending()
        }
        try {
            return withContext(Dispatchers.IO) {
                LoopbackListener(waitAtMost = waitAtMost).use { listener ->
                    val factory =
                        JvmCodeAuthFlowFactory(
                            webserverProvider = { listener },
                            openUrl = { url -> url.toString().also { mine.page = it }.let(browse) },
                        )
                    val client = desktopOidcClient(settings(), listener.redirectUri)
                    val response = factory.createAuthFlow(client).getAccessToken()
                    Tokens(response.access_token, response.refresh_token)
                }
            }
        } finally {
            pending.compareAndSet(mine, null)
        }
    }

    override suspend fun refresh(
        settings: SignInSettings,
        refreshToken: String,
    ): Tokens {
        val response = desktopOidcClient(settings, REGISTERED).refreshToken(refreshToken)
        // shildik rotates refresh tokens; a provider that does not answers none, and the old one stands.
        return Tokens(response.access_token, response.refresh_token ?: refreshToken)
    }

    companion object {
        /** The path the realm's client registers for the desktop (`shildik.desktopRedirectUri` in the chart). */
        const val PATH: String = "/signed-in"

        /** The address as registered — no port; the refresh names a return address but goes nowhere near it. */
        const val REGISTERED: String = "http://127.0.0.1$PATH"

        /** How long a sign-in waits for the provider's page to come back before it is abandoned. */
        val WAIT: Duration = 10.minutes
    }
}

/** The realm's client as [settings] name it, returning to [returnTo], its endpoints discovered. */
@OptIn(ExperimentalOpenIdConnect::class)
private suspend fun desktopOidcClient(
    settings: SignInSettings,
    returnTo: String,
): OpenIdConnectClient =
    OpenIdConnectClient(discoveryUri = "${settings.issuer}/.well-known/openid-configuration") {
        clientId = settings.clientId
        redirectUri = returnTo
        scope = settings.scope
    }.apply { discover() }

/**
 * Where the provider's page returns to on the desktop (B-178): a listener on `127.0.0.1` alone, on a port the system
 * picks — bound when it is made, before the browser opens, so [redirectUri] names a port that is already listening and
 * no other app's. The library's own listener binds a fixed port (8080, the local stack's) on every interface. It
 * answers the return once, with a page that says to go back to the app, and hands the address the provider returned
 * to — code and state in its query — to the flow, which checks the state and exchanges the code; no return within
 * [waitAtMost] throws [SignInAbandoned].
 */
@OptIn(ExperimentalOpenIdConnect::class)
internal class LoopbackListener(
    private val path: String = LoopbackSignInFlow.PATH,
    private val waitAtMost: Duration = LoopbackSignInFlow.WAIT,
) : Webserver,
    AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getByName(LOOPBACK), 0), 0)
    private val returned = CompletableDeferred<Url>()
    private val started = AtomicBoolean()
    private val stopped = AtomicBoolean()

    /** The address the provider is asked to return to: this listener's port, the registered path. */
    val redirectUri: String = "http://$LOOPBACK:${server.address.port}$path"

    init {
        server.createContext(path) { exchange -> exchange.use { answer(it) } }
    }

    private fun answer(exchange: HttpExchange) {
        // A context matches its path as a prefix; only the path itself, asked for as a page, is the return.
        if (exchange.requestMethod != "GET" || exchange.requestURI.rawPath != path || returned.isCompleted) {
            exchange.sendResponseHeaders(NOT_FOUND, -1)
            return
        }
        val query = exchange.requestURI.rawQuery
        val page = if (query.orEmpty().split('&').any { it.startsWith("code=") }) SIGNED_IN else NOT_SIGNED_IN
        val body = page.toByteArray()
        exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        exchange.responseHeaders.add("Cache-Control", "no-store")
        exchange.sendResponseHeaders(OK, body.size.toLong())
        exchange.responseBody.write(body)
        exchange.responseBody.close()
        returned.complete(Url(redirectUri + (query?.let { "?$it" } ?: "")))
    }

    override suspend fun startAndWaitForRedirect(redirectPath: String): Url {
        check(redirectPath == path) { "the flow waits on $redirectPath, the listener answers $path" }
        if (started.compareAndSet(false, true)) server.start()
        return withTimeoutOrNull(waitAtMost) { returned.await() } ?: throw SignInAbandoned(waitAtMost)
    }

    override suspend fun stop() = close()

    override fun close() {
        if (stopped.compareAndSet(false, true)) server.stop(0)
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val OK = 200
        const val NOT_FOUND = 404
        const val SIGNED_IN =
            "<!doctype html><html lang=\"en\"><meta charset=\"utf-8\"><title>Signed in to Haul</title>" +
                "<body style=\"font-family:sans-serif;margin:4em auto;max-width:32em\"><h1>Signed in to Haul</h1>" +
                "<p>You can close this tab and go back to the app.</p></body></html>"
        const val NOT_SIGNED_IN =
            "<!doctype html><html lang=\"en\"><meta charset=\"utf-8\"><title>Haul</title>" +
                "<body style=\"font-family:sans-serif;margin:4em auto;max-width:32em\">" +
                "<h1>The sign-in did not go through</h1><p>You can close this tab and try again in the app.</p></body></html>"
    }
}

/** A sign-in whose provider's page did not come back within [waited]: it ends as one that did not go through. */
internal class SignInAbandoned(
    waited: Duration,
) : Exception("the provider's page did not return within $waited")

/** The system has no browser to open the provider's page in: the sign-in ends at once. */
internal class SystemBrowserUnavailable(
    cause: Throwable? = null,
) : Exception("the system has no browser to open the sign-in in", cause)

/**
 * Opens [url] in the system's browser — AWT's, else `xdg-open` on a Linux desktop AWT does not know — or throws
 * [SystemBrowserUnavailable], so a sign-in on a system with none ends at once instead of waiting for a page nobody sees.
 */
internal fun systemBrowser(url: String) {
    val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
    try {
        when {
            desktop != null && desktop.isSupported(Desktop.Action.BROWSE) -> desktop.browse(URI(url))
            System.getProperty("os.name").orEmpty().startsWith("Linux") -> ProcessBuilder("xdg-open", url).start()
            else -> throw SystemBrowserUnavailable()
        }
    } catch (e: IOException) {
        throw SystemBrowserUnavailable(e)
    } catch (e: UnsupportedOperationException) {
        throw SystemBrowserUnavailable(e)
    }
}
