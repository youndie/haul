package io.github.youndie.haul.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import io.github.youndie.haul.feature.cart.CartCommands
import io.github.youndie.haul.feature.cart.LocalCartCommands
import io.github.youndie.haul.feature.identity.SignInActions
import io.github.youndie.haul.feature.product.ProductNotFound
import io.github.youndie.haul.feature.search.SearchSuggestOverlay
import io.github.youndie.haul.registry.haulRegistry
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.HaulHeaderView
import io.github.youndie.haul.ui.LocalHaulActions
import io.github.youndie.haul.ui.LocalHaulNow
import io.github.youndie.haul.ui.LocalLogoAction
import io.github.youndie.haul.ui.LocalSearchInput
import io.github.youndie.haul.ui.SearchFieldState
import io.github.youndie.haul.ui.SearchInput
import io.github.youndie.haul.ui.SearchSuggestPanel
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotRegistry
import io.github.youndie.kompot.KompotScreen
import io.github.youndie.kompot.KompotScreenLoader
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.withRefresh
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant

// The storefront's shell (research D2): the browser's address names a screen, the screen's tree comes
// from the server, and the shell draws what is between trees — loading, an error with Retry, a page
// that is not there. kompot does the loading (`KompotScreenLoader`), the drawing (`KompotScreen`) and
// `refresh` (`withRefresh`); the shell owns the address, the history and the search field.

/** The tag around a page still on its way, for the tests that tell loading from loaded. */
public const val LOADING_TAG: String = "page-loading"

/** How long typing has to pause before the suggest panel is asked for. */
internal const val SUGGEST_DELAY_MS: Long = 250

/**
 * Fetches the screen being shown again and draws it in place — no loading page, the scroll kept; a
 * fetch that fails leaves the page as it was. It is what kompot's `refresh` runs, and what anything
 * that changed the server's state from outside the tree's actions calls afterwards: sign-in (B-12), a
 * cart command (B-13).
 */
public fun interface ScreenRefresh {
    public fun refresh()
}

/** The refresh of the screen being shown; none outside the storefront's screens (a screenshot). */
public val LocalScreenRefresh: ProvidableCompositionLocal<ScreenRefresh?> = staticCompositionLocalOf { null }

/**
 * The storefront at the address [history] is at: every page from [transport], «now» from [clock] — one
 * clock, ticking each second, that every countdown reads. A tree's `navigate` to `/sign-in` is
 * sign-in's ([SignInActions]): [signIn] runs, then the screen is drawn again, signed in or not. The
 * cart's presses go to [cartCommands] (B-13), whose answer, `refresh`, draws the cart again.
 */
@Composable
public fun Storefront(
    transport: HaulTransport,
    history: BrowserHistory,
    signIn: suspend () -> Unit,
    clock: Clock = Clock.System,
    cartCommands: CartCommands? = null,
) {
    val navigator = remember(history) { Navigator(history) }
    DisposableEffect(navigator) {
        val stop = history.listen(navigator::arrived)
        onDispose { stop() }
    }
    val registry = remember { haulRegistry() }
    val now by rememberNow(clock)
    val focus = LocalFocusManager.current

    // The header's search field: typing asks for the suggest panel once the shopper pauses, Enter opens
    // the results. A query the server refuses, or a request that fails, shows no panel. The panel stays
    // open when the field loses focus — pressing a suggestion takes the focus, and closing the panel
    // under the press would lose the press — and closes on the scrim, an emptied field or a new page.
    val field = remember { SearchFieldState() }
    val search =
        remember(navigator) {
            SearchInput { text ->
                text.trim().ifEmpty { null }?.let { navigator.open(Address.search(it)) }
            }
        }
    var panel by remember { mutableStateOf<SearchSuggestPanel?>(null) }
    val typed = search.text.text.trim()
    LaunchedEffect(typed, search.typed) {
        if (!search.typed || typed.isEmpty()) {
            panel = null
        } else {
            delay(SUGGEST_DELAY_MS)
            panel = transport.suggest(typed)
        }
    }
    val dismiss = {
        panel = null
        focus.clearFocus()
    }
    LaunchedEffect(navigator.address) { dismiss() }

    // The header the client last drew: a page that is not there is drawn under it (Product_NotFound).
    var header by remember { mutableStateOf<HaulHeader?>(null) }
    val home = remember(navigator) { { navigator.open("/") } }
    // The suggest panel is drawn by the shell, not by a renderer, so its links are followed here.
    val panelActions = remember(navigator) { navigating(navigator) }
    CompositionLocalProvider(
        LocalHaulNow provides now,
        LocalSearchInput provides search,
        LocalLogoAction provides home,
        LocalHaulActions provides panelActions,
        LocalCartCommands provides cartCommands,
    ) {
        SearchSuggestOverlay(panel, highlighted = -1, field = field, onDismiss = dismiss) {
            val address = navigator.address
            key(address) {
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    KompotScreenLoader(
                        key = address,
                        load = { transport.tree(address.screen) },
                        loading = { Box(Modifier.testTag(LOADING_TAG)) { Loading(address) } },
                        failed = { cause, retry -> Failed(address, cause, header ?: SHELL_HEADER, retry, home) },
                    ) { tree ->
                        Shown(tree, address, transport, navigator, registry, signIn) { header = it }
                    }
                }
            }
        }
    }
}

/**
 * A screen's tree, following its actions: `/sign-in` is sign-in's, any other `navigate` opens its
 * deeplink, and `refresh` — kompot's action, or [LocalScreenRefresh] — draws the screen again in place.
 */
@Composable
private fun Shown(
    loaded: KompotComponent,
    address: Address,
    transport: HaulTransport,
    navigator: Navigator,
    registry: KompotRegistry,
    signIn: suspend () -> Unit,
    onHeader: (HaulHeader) -> Unit,
) {
    var tree by remember(loaded) { mutableStateOf(loaded) }
    LaunchedEffect(tree) { tree.header()?.let(onHeader) }
    val scope = rememberCoroutineScope()
    val refresh =
        remember(address) {
            ScreenRefresh { scope.launch { transport.treeOrNull(address.screen)?.let { tree = it } } }
        }
    val actions =
        remember(address) {
            val signInActions = SignInActions(signIn = signIn, redraw = refresh::refresh)
            val navigate = navigating(navigator)
            KompotActionHandler { action ->
                scope.launch { if (!signInActions.handle(action)) navigate.handle(action) }
            }.withRefresh(scope) { refresh.refresh() }
        }
    val forms = remember { FormController(FormSchema(formId = "none", fields = emptyList())) }
    CompositionLocalProvider(LocalScreenRefresh provides refresh) { KompotScreen(tree, registry, forms, actions) }
}

/** Follows `navigate` to its deeplink; anything else is somebody else's to handle. */
private fun navigating(navigator: Navigator): KompotActionHandler =
    KompotActionHandler { action -> if (action is NavigateAction) navigator.open(action.deeplink) }

private suspend fun HaulTransport.treeOrNull(path: String): KompotComponent? =
    try {
        tree(path)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failed: ScreenFailed) {
        null
    }

private fun KompotComponent.header(): HaulHeader? =
    when (this) {
        is HaulHeader -> this
        is ColumnComponent -> children.firstNotNullOfOrNull { it.header() }
        else -> null
    }

/** The page's placeholders while its tree is on its way. */
@Composable
private fun Loading(address: Address) {
    when (address.kind) {
        PageKind.Home -> HomeLoading()
        PageKind.Catalog -> CatalogLoading()
        PageKind.Product -> ProductLoading()
        PageKind.Search -> SearchLoading(address.query.orEmpty())
        PageKind.Cart -> CartLoading()
        PageKind.Other -> HaulHeaderView(SHELL_HEADER, pending = true)
    }
}

/**
 * The page when its tree did not arrive: not there (`404`) under the header last drawn, with the way
 * home; otherwise why, and Retry. A search keeps its query in the field.
 */
@Composable
private fun Failed(
    address: Address,
    cause: Throwable,
    header: HaulHeader,
    retry: () -> Unit,
    home: () -> Unit,
) {
    when {
        cause is ScreenFailed.NotFound && address.kind == PageKind.Product -> {
            ProductNotFound(header, onHome = home)
        }

        cause is ScreenFailed.NotFound -> {
            val subject = if (address.kind == PageKind.Catalog) "category" else "page"
            NotFoundShell(
                header = header,
                eyebrow = subject,
                title = "This $subject isn’t here",
                accent = "here",
                text = "The link may be old, or the page has moved.",
                actionLabel = "Go to the home page",
                onAction = home,
            )
        }

        address.kind == PageKind.Search -> {
            SearchError(address.query.orEmpty(), retry)
        }

        address.kind == PageKind.Cart -> {
            CartError(retry)
        }

        else -> {
            val failure = if (cause is ScreenFailed.Unreachable) ShellFailure.Unreachable else ShellFailure.Server
            ErrorShell(address.kind.subject, failure, retry)
        }
    }
}

private val PageKind.subject: String
    get() =
        when (this) {
            PageKind.Home -> "The home page"
            PageKind.Catalog -> "This category"
            PageKind.Product -> "This product"
            PageKind.Search, PageKind.Cart, PageKind.Other -> "This page"
        }

/** «Now», read from [clock] on each whole second: one ticking value every countdown on the page reads. */
@Composable
private fun rememberNow(clock: Clock): State<Instant> =
    produceState(clock.now(), clock) {
        while (true) {
            val now = clock.now()
            value = now
            delay(MILLIS_PER_SECOND - now.toEpochMilliseconds().mod(MILLIS_PER_SECOND))
        }
    }

private const val MILLIS_PER_SECOND = 1_000L
