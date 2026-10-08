package io.github.youndie.haul.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.youndie.haul.feature.identity.Identity
import io.github.youndie.haul.feature.identity.SignInActions
import io.github.youndie.haul.registry.haulJson
import io.github.youndie.haul.registry.haulRegistry
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotScreen
import io.github.youndie.kompot.KompotScreenLoader
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.standard.RefreshAction
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.PolymorphicSerializer

// **A stand-in until the app shell (B-35) lands**: the home page only, so that sign-in can be reached
// in the browser. The shell replaces this file; what it keeps is the identity seam used here —
// `Identity.send` around every request and `SignInActions.handle` before its own navigation.

/** The server's trees, fetched as whoever [identity] says the shopper is. */
public class Trees(
    private val http: HttpClient,
    private val identity: Identity,
) {
    public suspend fun load(path: String): KompotComponent {
        val response =
            try {
                identity.send { headers -> http.get(path) { headers.forEach { (name, value) -> header(name, value) } } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw TreeFailure(ShellFailure.Unreachable, e)
            }
        if (!response.status.isSuccess()) throw TreeFailure(ShellFailure.Server)
        return haulJson.decodeFromString(PolymorphicSerializer(KompotComponent::class), response.bodyAsText())
    }
}

/** Why a tree did not arrive. */
public class TreeFailure(
    public val failure: ShellFailure,
    cause: Throwable? = null,
) : Exception(failure.name, cause)

/** The home page from the server, the client's Loading and Error around it, and the header's sign-in. */
@Composable
public fun Storefront(
    trees: Trees,
    identity: Identity,
) {
    var generation by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val registry = remember { haulRegistry() }
    val forms = remember { FormController(FormSchema(formId = "none", fields = emptyList())) }
    val signIn = remember { SignInActions(signIn = identity::signIn, redraw = { generation++ }) }
    val handler =
        remember {
            KompotActionHandler { action ->
                scope.launch { if (!signIn.handle(action) && action is RefreshAction) generation++ }
            }
        }

    KompotScreenLoader(
        key = generation,
        load = { trees.load(HOME) },
        loading = { HomeLoading() },
        failed = { cause, retry -> ErrorShell("Home", (cause as? TreeFailure)?.failure ?: ShellFailure.Server, retry) },
    ) { screen ->
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            KompotScreen(screen, registry, forms, handler)
        }
    }
}

private const val HOME = "/ui/home"
