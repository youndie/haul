package io.github.youndie.haul.shell

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.feature.cart.CartCommands
import io.github.youndie.haul.feature.cart.LocalCartCommands
import io.github.youndie.haul.feature.catalog.FiltersSheetOverlay
import io.github.youndie.haul.feature.catalog.FiltersSheetState
import io.github.youndie.haul.feature.catalog.LocalFiltersSheet
import io.github.youndie.haul.feature.checkout.CheckoutCommands
import io.github.youndie.haul.feature.checkout.LocalCheckoutCommands
import io.github.youndie.haul.feature.home.PLUS_DIALOG_COMPACT_TOP
import io.github.youndie.haul.feature.identity.SessionControls
import io.github.youndie.haul.feature.identity.SignInActions
import io.github.youndie.haul.feature.order.OrderNotFound
import io.github.youndie.haul.feature.product.DialogOverlay
import io.github.youndie.haul.feature.product.ProductNotFound
import io.github.youndie.haul.feature.search.SearchSuggestOverlay
import io.github.youndie.haul.registry.haulRegistry
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.HaulHeaderView
import io.github.youndie.haul.ui.HeaderMenuState
import io.github.youndie.haul.ui.LocalHaulActions
import io.github.youndie.haul.ui.LocalHaulNow
import io.github.youndie.haul.ui.LocalHeaderMeasured
import io.github.youndie.haul.ui.LocalHeaderMenu
import io.github.youndie.haul.ui.LocalLogoAction
import io.github.youndie.haul.ui.LocalSearchInput
import io.github.youndie.haul.ui.PlusTrialDialog
import io.github.youndie.haul.ui.SearchFieldState
import io.github.youndie.haul.ui.SearchInput
import io.github.youndie.haul.ui.SearchSuggestPanel
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotLoadState
import io.github.youndie.kompot.KompotNodeOverrides
import io.github.youndie.kompot.KompotRealtimeProvider
import io.github.youndie.kompot.KompotRegistry
import io.github.youndie.kompot.KompotScreen
import io.github.youndie.kompot.KompotScreenLoader
import io.github.youndie.kompot.LocalKompotNodeOverrides
import io.github.youndie.kompot.commands.LoadAction
import io.github.youndie.kompot.commands.UpdateHistory
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.realtime.KompotRealtimeSource
import io.github.youndie.kompot.rememberKompotScreenLoaderState
import io.github.youndie.kompot.standard.CloseAction
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.PresentAction
import io.github.youndie.kompot.standard.SequenceAction
import io.github.youndie.kompot.withLoad
import io.github.youndie.kompot.withRefresh
import io.github.youndie.kompot.withUpdates
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

/** The tag on the page's scroll, for the tests that tell a kept page's scroll from a new one's. */
public const val PAGE_SCROLL_TAG: String = "page-scroll"

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
 * clock, ticking each second, that every countdown reads. A `navigate` to `/sign-in` is sign-in's
 * ([SignInActions]) wherever it is pressed — a tree, the shell's own header, a prompt ([Signing], B-66):
 * [signIn] runs, then the page its `next` names opens (B-41), or the screen is drawn again, signed in or
 * not; a popup the browser blocked opens the sign-in page, which says so and signs in in this tab
 * through [session]. `/sign-in` itself — a reload, a shared link, back or forward — is that page
 * ([SignInPage]), drawn by the client. The header's «Sign out» (`/sign-out`) forgets the customer
 * through [session] and draws the page again for a guest, or goes home from a customer's page. A page
 * refused for want of a sign-in (`401`: a guest on a customer's page, or a sign-in lapsed past
 * renewing) asks for one ([SignInPrompt], B-44) and is loaded again once it has gone through. The cart's presses — the cart's own and a card's «+» — go
 * to [cartCommands] (B-13, B-37), whose answer, `refresh`, draws the screen again — or, for «+» and «Add to
 * cart», `update`, which redraws the header and the control (B-63). «Clear» on recent
 * searches goes through [commands] (B-37), and the suggest panel is asked for again once the server
 * has answered. The checkout's go to [checkoutCommands] (B-15), whose `refresh` draws it again the
 * same way. A tree's `present` draws its component over the page — the product page's review and
 * question dialogs (B-22), an order's return dialog (B-21) and the Haul Plus trial's (B-23) — until a
 * `close`, a new page or the scrim takes it away; their commands, and «Helpful» on a review (B-43), go to
 * [treeCommands] (B-51). A screen that names a channel — the order's page (B-29) — listens on it through
 * [realtime] while it is shown, and each update redraws its node in place; with no [realtime] — a
 * screenshot — it is drawn as it loaded. The phone's filter sheet is held here, above the page
 * ([FiltersSheetState], B-54): it stays open over the pages its own presses open, and any other new page
 * closes it. So is the phone header's menu ([HeaderMenuState], B-73), which any new page closes.
 *
 * A screen is a path (B-62). A new address on the same path — a facet, a sort, a page of results, back
 * or forward between two of them — loads behind the page that is drawn, which keeps its scroll and what
 * its nodes hold open, with a [LoadingLine] under the header until the tree arrives; one that does not
 * arrive leaves the page as it was under a [NotUpdatedNotice] with Retry. An address on another path
 * draws its placeholder and starts at the top. A customer's page refused for a lapsed sign-in is not
 * kept: it is taken down and asks for the sign-in as above.
 *
 * A press that only filters, sorts or pages the screen is a `load` (B-63): one `GET`, answered with an
 * `update` of the nodes it changes, which go into the screen's override store, and the address they make,
 * which the history takes without a load ([Navigator.record]). The line under the header is on while it
 * is on its way; one that does not arrive leaves the page under the same notice, whose Retry presses it
 * again. A cart command answers `update` the same way — the header's count and the control pressed. Back
 * and forward to an address recorded so load its page as any visit does, and the page that arrives
 * replaces whatever the updates drew.
 */
@Composable
public fun Storefront(
    transport: HaulTransport,
    history: BrowserHistory,
    signIn: suspend () -> Unit,
    clock: Clock = Clock.System,
    cartCommands: CartCommands? = null,
    commands: HaulCommands? = null,
    checkoutCommands: CheckoutCommands? = null,
    treeCommands: TreeCommands? = null,
    realtime: KompotRealtimeSource? = null,
    session: SessionControls? = null,
) {
    val navigator = remember(history) { Navigator(history) }
    DisposableEffect(navigator) {
        val stop = history.listen(navigator::arrived)
        onDispose { stop() }
    }
    val registry = remember { haulRegistry() }
    val now by rememberNow(clock)
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()

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
    val filters = remember { FiltersSheetState() }
    val menu = remember { HeaderMenuState() }
    // A page visited closes the panel, the filter sheet and the phone header's menu (B-73); an address an
    // update recorded (B-63) is the page already drawn, and closes none — the sheet's own presses are such
    // updates.
    LaunchedEffect(navigator.visits) {
        dismiss()
        filters.close()
        menu.close()
    }
    // «Clear» on recent searches: the panel is asked for again once the server has emptied them.
    val clearUrl = panel?.clearUrl
    val clearRecent =
        if (commands == null || clearUrl == null) {
            null
        } else {
            {
                scope.launch {
                    if (commands.answered { send("DELETE", clearUrl, null) }) panel = transport.suggest(typed)
                }
                Unit
            }
        }

    // The header the client last drew: a page that is not there is drawn under it (Product_NotFound).
    var header by remember { mutableStateOf<HaulHeader?>(null) }
    var signedOut by remember { mutableIntStateOf(0) }
    val home = remember(navigator) { { navigator.open("/") } }
    // The sign-in page a blocked popup led to: it says why (B-66).
    var blockedAt by remember { mutableStateOf<String?>(null) }
    val signing = remember(navigator, signIn, session) { Signing(signIn, session, navigator) { blockedAt = it } }
    // The suggest panel and the header of a page the shell draws itself — loading, an error, a page that is
    // not there, a prompt, the sign-in page — are not a renderer's, so their links are followed here; sign-in's
    // and sign-out's as everywhere else (B-66), the page loaded again for who is looking once they are done.
    val panelActions =
        remember(navigator, signing) {
            val navigate = navigating(navigator)
            KompotActionHandler { action ->
                if (SignInActions.claims(action)) {
                    scope.launch { signing.actions(navigator.address, redraw = navigator::reload).handle(action) }
                } else {
                    navigate.handle(action)
                }
            }
        }
    CompositionLocalProvider(
        LocalHaulNow provides now,
        LocalSearchInput provides search,
        LocalLogoAction provides home,
        LocalHaulActions provides panelActions,
        LocalCartCommands provides cartCommands,
        LocalCheckoutCommands provides checkoutCommands,
        LocalTreeCommands provides treeCommands,
        LocalFiltersSheet provides filters,
        LocalHeaderMenu provides menu,
    ) {
        SearchSuggestOverlay(panel, highlighted = -1, field = field, onDismiss = dismiss, onClear = clearRecent) {
            val address = navigator.address
            val visit = navigator.visits
            // A screen is a path (B-62): `/c/mugs?brand=Ostra` loads behind the drawn `/c/mugs`, which keeps
            // its scroll and whatever its nodes hold open; another path is another screen, drawn from its
            // placeholder at the top. [signedOut] counts the pages a lapsed sign-in took down: a customer's
            // page is not kept for a shopper who is a guest now.
            val screen = address.path to signedOut
            if (address.kind == PageKind.SignIn) {
                // The client's own page (B-66): no tree to load, and no filter sheet over it; one per visit.
                key(visit) {
                    Scrolled(rememberScrollState()) {
                        val blocked = blockedAt == address.value
                        SignInPage(address, header ?: SHELL_HEADER, signing, navigator, blocked)
                    }
                }
                return@SearchSuggestOverlay
            }
            // The screen's `load`s (B-63): whether one is on its way, and which press is the last — the only
            // one whose answer is run. Held above the screen, so the filter sheet over it reads it too.
            val loading = remember(screen) { KompotLoadState() }
            key(screen) {
                // Where the screen's loads are: the tree drawn, a load on its way, why the last one failed.
                val loader = rememberKompotScreenLoaderState()
                val scroll = rememberScrollState()
                // What a `present` put over the page; a new address starts without one.
                var presented by remember(address) { mutableStateOf<Presented?>(null) }
                // A page refused for want of a sign-in asks for one (B-44); [loads] counts the visit's loads
                // after a sign-in, and [signedInFor] is the load a sign-in from here asked for — refused again,
                // that one is an error page, not another prompt. Per visit, not per address: an address an
                // update recorded (B-63) is no new page to load.
                var loads by remember(visit) { mutableIntStateOf(0) }
                var signedInFor by remember(visit) { mutableIntStateOf(-1) }
                // The store the screen's updates and the order's channel write into (B-63), one per screen. A
                // page that arrives is the truth for its address and drops what they wrote, even when it is
                // equal to the page drawn — back to the address before a filter brings exactly that page: each
                // load the loader completes is an arrival (kompot §4.4, B-64).
                val overrides = remember { KompotNodeOverrides() }
                // A `load` whose answer did not arrive, and how to press it again; a new press or visit forgets it.
                var unanswered by remember(visit) { mutableStateOf<Unanswered?>(null) }
                // The channel the page's tree named, when it named one (B-29).
                var topic by remember { mutableStateOf<String?>(null) }
                val live = remember(topic, realtime) { realtime?.let { source -> topic?.let { Live(it, source) } } }
                // How tall the page's header is drawn: the line of a load on its way sits under it.
                var headerHeight by remember { mutableIntStateOf(0) }
                val measured = remember { { height: Int -> headerHeight = height } }
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalHeaderMeasured provides measured) {
                        KompotScreenLoader(
                            key = visit to loads,
                            screenKey = screen,
                            state = loader,
                            load = {
                                transport
                                    .screen(address.screen)
                                    .also { topic = it.realtimeTopic }
                                    .tree
                            },
                            loading = { Scrolled(scroll) { Box(Modifier.testTag(LOADING_TAG)) { Loading(address) } } },
                            failed = { cause, retry ->
                                when {
                                    loader.screen == null -> {
                                        Scrolled(scroll) {
                                            if (cause.asksForSignIn && signedInFor != loads) {
                                                SignInPrompt(address, header ?: SHELL_HEADER, signing, navigator) {
                                                    loads += 1
                                                    signedInFor = loads
                                                }
                                            } else {
                                                Failed(address, cause, header ?: SHELL_HEADER, retry, home)
                                            }
                                        }
                                    }

                                    cause.asksForSignIn -> {
                                        LaunchedEffect(cause) { signedOut += 1 }
                                    }

                                    else -> {
                                        Box(
                                            Modifier.fillMaxSize().padding(16.dp),
                                            contentAlignment = Alignment.BottomCenter,
                                        ) {
                                            NotUpdatedNotice(cause, retry)
                                        }
                                    }
                                }
                            },
                        ) { tree ->
                            Scrolled(scroll) {
                                Shown(
                                    tree,
                                    address,
                                    transport,
                                    navigator,
                                    registry,
                                    signing,
                                    live,
                                    InPlace(overrides, loading) { unanswered = it },
                                    { header = it },
                                    { signedOut += 1 },
                                ) {
                                    presented =
                                        it
                                }
                            }
                        }
                    }
                    if ((loader.isLoading || loading.isLoading) && loader.screen != null) {
                        LoadingLine(Modifier.offset { IntOffset(0, (headerHeight - scroll.value).coerceAtLeast(0)) })
                    }
                    unanswered?.let { press ->
                        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
                            NotUpdatedNotice(press.cause, press.retry)
                        }
                    }
                    presented?.let { shown ->
                        val forms = remember { FormController(FormSchema(formId = "none", fields = emptyList())) }
                        val top = if (shown.content is PlusTrialDialog) PLUS_DIALOG_COMPACT_TOP else 120.dp
                        DialogOverlay(onDismiss = { presented = null }, compactTop = top) {
                            KompotScreen(shown.content, registry, forms, shown.actions)
                        }
                    }
                }
            }
            FiltersSheetOverlay(filters, loading.isLoading)
        }
    }
}

/** The page's own scroll: one per screen, so a new address of the screen keeps it and another path starts at the top. */
@Composable
private fun Scrolled(
    scroll: ScrollState,
    content: @Composable () -> Unit,
) {
    Box(Modifier.testTag(PAGE_SCROLL_TAG).fillMaxSize().verticalScroll(scroll)) { content() }
}

/** The channel a screen named ([topic]) and where its updates come from. */
internal class Live(
    val topic: String,
    val source: KompotRealtimeSource,
)

/**
 * What a screen's presses change in place (B-63): the store [overrides] its `update`s go into, its `load`s
 * ([loading]), and where a `load` whose answer did not arrive is told ([unanswered]; `null` when the next
 * one starts).
 */
internal class InPlace(
    val overrides: KompotNodeOverrides,
    val loading: KompotLoadState,
    val unanswered: (Unanswered?) -> Unit,
)

/** A `load` whose answer did not arrive: why, and the same press again. */
internal class Unanswered(
    val cause: Throwable,
    val retry: () -> Unit,
)

/** A `load` answered with nothing to do: it failed, and the shell has said so. */
private val NOTHING: KompotAction = SequenceAction(emptyList())

/** A component a tree's `present` shows over the page, and the screen's handler its actions go to. */
internal class Presented(
    val content: KompotComponent,
    val actions: KompotActionHandler,
)

/**
 * A screen's tree, following its actions: `/sign-in` is sign-in's, any other `navigate` opens its
 * deeplink, and `refresh` — kompot's action, or [LocalScreenRefresh] — draws the screen again in place;
 * a refresh refused for want of a sign-in (a lapsed one, B-44) is [onSignedOut]'s. A `load` GETs its
 * answer, and an `update` — a load's or a command's — replaces its nodes in [inPlace]'s store and hands
 * the address it names to the history without a load (B-63); a load refused for a lapsed sign-in is
 * [onSignedOut]'s too, any other failure [InPlace.unanswered]'s.
 * `present` and `close` are [onPresent]'s, and a `sequence` is each of its actions in turn. A [live] screen
 * listens on its channel while it is shown, and an update redraws the node it names (B-29); a refresh starts it
 * over from the tree it brought, so an update older than the tree never covers it.
 */
@Composable
private fun Shown(
    loaded: KompotComponent,
    address: Address,
    transport: HaulTransport,
    navigator: Navigator,
    registry: KompotRegistry,
    signing: Signing,
    live: Live?,
    inPlace: InPlace,
    onHeader: (HaulHeader) -> Unit,
    onSignedOut: () -> Unit,
    onPresent: (Presented?) -> Unit,
) {
    var tree by remember(loaded) { mutableStateOf(loaded) }
    // The trees a refresh brought: fetched here, not by the loader, so the screen is told of each one — an
    // arrival drops the overrides even when the tree is equal to the one drawn (kompot §4.4, B-64).
    var refreshed by remember { mutableIntStateOf(0) }
    LaunchedEffect(tree) { tree.header()?.let(onHeader) }
    val scope = rememberCoroutineScope()
    val refresh =
        remember(address) {
            ScreenRefresh {
                scope.launch {
                    transport.treeOrNull(address.screen, onSignedOut)?.let {
                        tree = it
                        refreshed += 1
                    }
                }
            }
        }
    val actions =
        remember(address, inPlace.overrides) {
            // A sign-in that returns to the page already shown draws it again: opening it would do nothing.
            val signInActions =
                signing.actions(address, redraw = refresh::refresh) { next ->
                    if (next == address.value) refresh.refresh() else navigator.open(next)
                }
            val navigate = navigating(navigator)
            lateinit var top: KompotActionHandler
            top =
                presenting(
                    KompotActionHandler { action ->
                        scope.launch { if (!signInActions.handle(action)) navigate.handle(action) }
                    }.withRefresh(scope) { refresh.refresh() }
                        .withUpdates(inPlace.overrides) { deeplink, history ->
                            navigator.record(deeplink, replace = history == UpdateHistory.REPLACE)
                        }
                        // Above `withUpdates`: a load's answer goes down the chain from here.
                        .withLoad(scope, inPlace.loading) { url ->
                            transport.parts(url, onSignedOut, inPlace.unanswered) { top.handle(LoadAction(url)) }
                        },
                    onPresent,
                )
            top
        }
    val forms = remember { FormController(FormSchema(formId = "none", fields = emptyList())) }
    CompositionLocalProvider(LocalScreenRefresh provides refresh, LocalKompotNodeOverrides provides inPlace.overrides) {
        if (live == null) {
            KompotScreen(tree, registry, forms, actions, arrival = refreshed)
        } else {
            KompotRealtimeProvider(
                live.topic,
                live.source,
                { KompotScreen(tree, registry, forms, actions, arrival = refreshed) },
            )
        }
    }
}

/**
 * Whether [send] got an answer at all — a refusal is an answer, and the panel asked for again shows
 * what the server now has. No answer leaves the panel as it was; the next press tries again.
 */
@Suppress(
    "ktlint:kapkan:swallowed-failure",
    "A command that got no answer changed nothing the shopper can see; the page stays as the server last drew it.",
)
private suspend fun HaulCommands.answered(send: suspend HaulCommands.() -> HaulResponse): Boolean =
    try {
        send()
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        // Throwable: in the browser a failed fetch is a JavaScript error, which is no `Exception` on Wasm.
        false
    }

/**
 * kompot's `present`, `close` and `sequence` (B-22): a `present` shows its component over the page with
 * this handler for its actions, a `close` takes it away, a `sequence` is each of its actions in order —
 * a dialog's answer is `close`, then `refresh`. Anything else is [next]'s.
 */
internal fun presenting(
    next: KompotActionHandler,
    show: (Presented?) -> Unit,
): KompotActionHandler {
    lateinit var self: KompotActionHandler
    self =
        KompotActionHandler { action ->
            when (action) {
                is SequenceAction -> action.actions.forEach(self::handle)
                is PresentAction -> show(Presented(action.content, self))
                CloseAction -> show(null)
                else -> next.handle(action)
            }
        }
    return self
}

/** Follows `navigate` to its deeplink; anything else is somebody else's to handle. */
private fun navigating(navigator: Navigator): KompotActionHandler =
    KompotActionHandler { action -> if (action is NavigateAction) navigator.open(action.deeplink) }

/**
 * The answer of the `load` endpoint at [url] (B-63), or [NOTHING] when it did not arrive — after telling
 * [signedOut] when that was a `401`, or [unanswered] why, with [retry]. A new press forgets the last failure.
 */
private suspend fun HaulTransport.parts(
    url: String,
    signedOut: () -> Unit,
    unanswered: (Unanswered?) -> Unit,
    retry: () -> Unit,
): KompotAction {
    unanswered(null)
    return try {
        action(url)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failed: ScreenFailed) {
        if (failed.asksForSignIn) signedOut() else unanswered(Unanswered(failed, retry))
        NOTHING
    }
}

/** The tree at [path], or `null` when it did not arrive — after telling [signedOut] when that was a `401`. */
private suspend fun HaulTransport.treeOrNull(
    path: String,
    signedOut: () -> Unit,
): KompotComponent? =
    try {
        tree(path)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failed: ScreenFailed) {
        if (failed.asksForSignIn) signedOut()
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
        PageKind.Checkout -> CheckoutLoading()
        PageKind.Account -> AccountLoading()
        PageKind.Saved -> SavedLoading()
        PageKind.Order -> OrderLoading()
        PageKind.SignIn, PageKind.Other -> HaulHeaderView(SHELL_HEADER, pending = true)
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

        cause is ScreenFailed.NotFound && address.kind == PageKind.Order -> {
            OrderNotFound(header)
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

        address.kind == PageKind.Checkout -> {
            CheckoutError(retry)
        }

        address.kind == PageKind.Account -> {
            AccountError(retry)
        }

        address.kind == PageKind.Saved -> {
            SavedError(retry)
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

            PageKind.Order -> "This order"

            PageKind.Search,
            PageKind.Cart,
            PageKind.Checkout,
            PageKind.Account,
            PageKind.Saved,
            PageKind.SignIn,
            PageKind.Other,
            -> "This page"
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
