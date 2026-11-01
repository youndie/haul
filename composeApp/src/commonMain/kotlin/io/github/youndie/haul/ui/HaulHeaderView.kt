package io.github.youndie.haul.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.feature.identity.SignInActions
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.LocalHaulCompact
import io.github.youndie.haul.theme.LocalHaulMedium
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.standard.NavigateAction
import kotlinx.coroutines.launch

/**
 * The header's search field as the page around the header sees it: whether the shopper is typing in
 * it — the field then draws its caret — and, once laid out, where it is, so the suggest panel can open
 * under it. [scrimTop] is where the panel's scrim starts: the header's bottom at 1440, the field's
 * row on a phone (Search_Autocomplete). Both in root coordinates, pixels.
 */
@Stable
public class SearchFieldState(
    focused: Boolean = false,
) {
    public var focused: Boolean by mutableStateOf(focused)
    public var bounds: Rect? by mutableStateOf(null)
        internal set
    public var scrimTop: Float? by mutableStateOf(null)
        internal set
}

/** The search field's state, provided by whoever owns the field's focus; none is a field nobody types in. */
public val LocalSearchField: ProvidableCompositionLocal<SearchFieldState?> = staticCompositionLocalOf { null }

/**
 * What the shopper types into the header's search field, owned by the app's shell; [submit] hands the
 * text and the [scope] the field's picker chose to [onSubmit], which opens the results. Without one — a
 * screenshot — the field is drawn, not edited, and the picker opens nothing.
 *
 * The suggest panel's queries are [offer]ed to it (B-77): the arrow keys move [highlighted] over them, and
 * Enter on a highlighted one hands its action to [onPick] instead of submitting the text. Typing starts the
 * highlight over, as a new panel does.
 */
@Stable
public class SearchInput(
    private val onPick: (KompotAction) -> Unit = {},
    private val onSubmit: (text: String, scope: String?) -> Unit,
) {
    public var text: TextFieldValue by mutableStateOf(TextFieldValue())
        private set

    /** Whether [text] is the shopper's own typing, rather than the query the page arrived with. */
    public var typed: Boolean by mutableStateOf(false)
        private set

    /**
     * The category the search is in (B-72): a `category` of the header's `scopes`, `null` for all of them. A page
     * arriving sets it to its own; the picker changes it, and nothing else does.
     */
    public var scope: String? by mutableStateOf(null)
        private set

    /** The query of the panel the keyboard is on, from 0; -1 is none, the text as typed. */
    public var highlighted: Int by mutableIntStateOf(-1)
        private set

    private var offered: List<KompotAction?> = emptyList()

    /** The shopper changed the text (or only moved the caret). */
    public fun type(value: TextFieldValue) {
        if (value.text != text.text) {
            typed = true
            highlighted = -1
        }
        text = value
    }

    /**
     * A page arrived holding [query] (none outside search) in [scope]: the field shows it, caret at its end,
     * and the picker the scope.
     */
    public fun show(
        query: String,
        scope: String? = null,
    ) {
        text = TextFieldValue(query, TextRange(query.length))
        typed = false
        this.scope = scope
    }

    /** The picker chose [scope]: the next search is in it. */
    public fun choose(scope: String?) {
        this.scope = scope
    }

    /** The queries the panel now shows, each with where it goes; none when the panel is closed. */
    public fun offer(actions: List<KompotAction?>) {
        offered = actions
        highlighted = -1
    }

    /** Whether the panel offers queries, so the arrow keys are the panel's rather than the field's. */
    public val choosing: Boolean get() = offered.isNotEmpty()

    /**
     * The arrow keys: [by] queries down (up when negative), from none to the last and back to none above the
     * first. Whether there was anything to move over — without a panel the keys are the field's own.
     */
    public fun move(by: Int): Boolean {
        if (!choosing) return false
        highlighted = (highlighted + by).coerceIn(-1, offered.lastIndex)
        return true
    }

    /**
     * Enter: the highlighted query, or the text as typed when none is. Either ends the typing: the text is now the
     * query the results arrive with, and a suggest the pause was still waiting for is not asked (B-121).
     */
    public fun submit() {
        typed = false
        val picked = offered.getOrNull(highlighted)
        if (picked != null) onPick(picked) else onSubmit(text.text, scope)
    }
}

/** The header's search field as the shopper edits it; none draws the field as the tree has it. */
public val LocalSearchInput: ProvidableCompositionLocal<SearchInput?> = staticCompositionLocalOf { null }

/**
 * Asks the server for the header alone when a press needs what only it carries (B-138): the sign-in page landed on
 * cold draws the client's own header, whose category words all lead to the catalog's root and whose picker has no
 * scopes, and asks for nothing while it loads (B-66). The phone header's menu button and the search picker ask
 * through it; the header that answers is drawn in place of the client's. None — every other page, a screenshot —
 * asks nothing.
 */
public val LocalAskForHeader: ProvidableCompositionLocal<(() -> Unit)?> = staticCompositionLocalOf { null }

/** Where pressing the logo goes — the home page, which is the app's own; none leaves the logo inert. */
public val LocalLogoAction: ProvidableCompositionLocal<(() -> Unit)?> = staticCompositionLocalOf { null }

/**
 * Told the height a page's header is drawn at, in pixels: the shell draws the line of a load on its way
 * under it (B-62). None outside the storefront.
 */
public val LocalHeaderMeasured: ProvidableCompositionLocal<((height: Int) -> Unit)?> = staticCompositionLocalOf { null }

/**
 * Told every header a page's tree draws, as it is drawn — the tree's, or what an `update` put in its place (a cart
 * command's count, B-63): the shell keeps the last one over the next page's placeholder (B-170). None outside the
 * storefront.
 */
public val LocalHeaderDrawn: ProvidableCompositionLocal<((HaulHeader) -> Unit)?> = staticCompositionLocalOf { null }

/** [this], reporting its height to [LocalHeaderMeasured] when the shell asks for it. */
@Composable
internal fun Modifier.headerMeasured(): Modifier {
    val report = LocalHeaderMeasured.current ?: return this
    return onSizeChanged { report(it.height) }
}

/**
 * The header, at the width the page is drawn at (`HaulHeader` on the wire). [pending] is the client's
 * own header before any tree has arrived (Loading, Error): who is looking is not known yet, so the
 * account slot is a placeholder and the cart has no count. [onAccount] is a tap on a guest's «Sign in»,
 * and the renderer hands it `HaulHeader.account`; a customer's name opens the account's menu — the
 * account and «Sign out» (B-66) — whose entries follow through [LocalHaulActions].
 */
@Composable
public fun HaulHeaderView(
    header: HaulHeader,
    modifier: Modifier = Modifier,
    pending: Boolean = false,
    onAccount: (() -> Unit)? = null,
) {
    // A header the shell draws itself — over a page that did not load, or is not there — has no renderer to
    // hand it the account's action, and follows it through whoever follows the page's links (B-66).
    val press = onAccount ?: following(header.account)
    val account = accountTap(if (pending) null else press, header.customerName)
    val compact = LocalHaulCompact.current
    val field = LocalSearchField.current
    Column(
        modifier
            .fillMaxWidth()
            .background(HaulColors.surfaceContainerLowest)
            .then(
                if (field != null && !compact) {
                    Modifier.onGloballyPositioned { field.scrimTop = it.boundsInRoot().bottom }
                } else {
                    Modifier
                },
            ),
    ) {
        if (compact) {
            CompactHeader(header, pending, account, press)
        } else {
            WideHeader(header, pending, account, medium = LocalHaulMedium.current)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(HaulColors.outlineVariant))
    }
}

/** What «Sign out» is called, in the account slot's menu and the phone's header menu. */
public const val SIGN_OUT_LABEL: String = "Sign out"

/** The header's «Sign out» (B-66): the client's own address, answered by sign-in's actions, not followed. */
public val SIGN_OUT_ACTION: NavigateAction = NavigateAction(SignInActions.SIGN_OUT)

/**
 * A customer's account slot opens a menu (B-66): the account the tree names (`HaulHeader.account`) and
 * «Sign out», the one place a customer signs out. No artboard draws it open; closed, the slot is drawn as
 * the canvas has it.
 */
private val HaulHeader.accountMenu: List<Link>
    get() = listOf(Link("Account", account), Link(SIGN_OUT_LABEL, SIGN_OUT_ACTION))

/**
 * The account slot's tap, without a drawn indication: the canvas draws no pressed state, and a
 * screenshot of a header must not depend on whether it is tappable.
 */
private fun accountTap(
    onAccount: (() -> Unit)?,
    customerName: String?,
): Modifier =
    if (onAccount == null) {
        Modifier
    } else {
        Modifier.handOverWords().sinksWhenPressed().pressesAgain(onAccount).clickable(
            interactionSource = null,
            indication = null,
            onClickLabel = if (customerName == null) "Sign in" else "Account",
            role = Role.Button,
            onClick = onAccount,
        )
    }

/**
 * The header at 1440, and at any desktop width (B-82): below 1280 ([medium]) the search field takes a row of its
 * own under the logo's, where 1440's single row left it a sliver at 1024; the category row draws the words that
 * fit whole and keeps «HAUL PLUS» at its end, where it used to be squeezed into a column of letters. «Catalog»
 * lists every category, so a word that does not fit is still a press away.
 */
@Composable
private fun WideHeader(
    header: HaulHeader,
    pending: Boolean,
    account: Modifier,
    medium: Boolean,
) {
    // Where the store delivers and for how much (B-72): two facts, nothing to press. The canvas's «Sell on
    // HAUL», «Help» and «EN · USD» had no page behind them and are not drawn.
    Strip(height = 36.dp, padding = 48.dp, size = 11f, spacing = 0.06f) { style ->
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            DeliverTo(header.deliverTo, style)
            Text(header.deliveryPromise.uppercase(), style)
        }
    }
    val search =
        @Composable { modifier: Modifier ->
            SearchField(
                header,
                modifier,
                height = 56.dp,
                radius = 16.dp,
                start = 20.dp,
                end = 4.dp,
                gap = 14.dp,
                textSize = 17f,
                button = 44.dp,
                buttonRadius = 12.dp,
                glyph = 22.dp,
            ) {
                Box(Modifier.width(1.dp).height(28.dp).background(HaulColors.outlineVariant))
                ScopePicker(header)
            }
        }
    Row(
        Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Logo(
            size = 44f,
            dot = 11.dp,
            dotMargin = 3.dp,
            modifier = Modifier.width(132.dp).pressable(LocalLogoAction.current),
        )
        // «Catalog» opens every top-level category as a menu (`HaulHeader.catalog`).
        LinkMenu(header.catalog) { press ->
            Row(
                press
                    .height(56.dp)
                    .background(HaulColors.primary, RoundedCornerShape(16.dp))
                    .padding(start = 18.dp, end = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(HaulIcons.grid, 20.dp, HaulColors.onPrimary)
                Text("Catalog", HaulType.text(16f, 700).copy(color = HaulColors.onPrimary))
            }
        }
        if (medium) Spacer(Modifier.weight(1f)) else search(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            // «Orders» goes where the tree says (`HaulHeader.orders`): a customer's orders, a guest's sign-in.
            Shortcut(HaulIcons.box, "Orders", modifier = Modifier.follows(header.orders))
            // «Saved» likewise (`HaulHeader.saved`, B-20): a customer's Saved list, a guest's sign-in.
            Shortcut(HaulIcons.heart, SAVED, modifier = Modifier.follows(header.saved))
            when {
                pending -> {
                    Shortcut(HaulIcons.person) { Skeleton(Modifier.width(40.dp).height(10.dp), 5.dp) }
                }

                header.customerName == null -> {
                    Shortcut(HaulIcons.person, "Sign in", weight = 700, modifier = account)
                }

                else -> {
                    LinkMenu(header.accountMenu, alignEnd = true) { press ->
                        Shortcut(HaulIcons.person, header.customerName.orEmpty(), modifier = press)
                    }
                }
            }
            CartButton(
                if (pending) 0 else header.cartCount,
                Modifier.padding(start = 6.dp).follows(header.cart),
                height = 56.dp,
                radius = 16.dp,
                horizontal = 18.dp,
                gap = 10.dp,
                label = true,
            )
        }
    }
    if (medium) {
        // The row's own 20 px above the field's 56, as the logo's row has around its 56 px controls.
        search(Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, bottom = 20.dp))
    }
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Deals(size = 15f, Modifier.follows(header.deals))
        FittingWords(header.categories, 28.dp, HaulType.text(15f, 500), Modifier.weight(1f), links = header.catalog)
        // «Try as a demo shopper» (B-162), while the server offers one: on the row whose words give way, so it keeps
        // its whole name at every desktop width — beside «Sign in» on the row above it squeezed the search field to
        // a sliver at 1280.
        if (!pending && header.customerName == null) header.demo?.let { DemoShopperPill(it) }
        // The Plus offer (`HaulHeader.plus`, B-49): the trial's dialog, a member's account, a guest's sign-in.
        Text(
            "HAUL PLUS",
            HaulType.label(11f, 600, 0.06f),
            Modifier
                .testTag(PLUS_PILL_TAG)
                .follows(header.plus)
                .background(
                    HaulColors.secondaryContainer,
                    CircleShape,
                ).padding(horizontal = 12.dp, vertical = 7.dp),
            softWrap = false,
        )
    }
}

/**
 * The wide header's «Try as a demo shopper» (B-162): a pill at the end of the category row, before «HAUL PLUS», in
 * the colour of what it does — it signs the visitor in.
 */
@Composable
private fun DemoShopperPill(action: KompotAction) {
    Text(
        DEMO_SHOPPER_NAME,
        HaulType.text(13f, 700).copy(color = HaulColors.onPrimary),
        Modifier
            .testTag(DEMO_SHOPPER_TAG)
            .follows(action)
            .background(HaulColors.primary, CircleShape)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        softWrap = false,
    )
}

/** What the demo shopper's control says (B-162): the wide header's pill, the phone's menu row. */
public const val DEMO_SHOPPER_NAME: String = "Try as a demo shopper"

/** The tag of the wide header's demo pill, for the tests that press it. */
public const val DEMO_SHOPPER_TAG: String = "demo-shopper"

/** The name of the search field's button, an icon (B-126). */
private const val SEARCH = "Search"

/** «Saved»: the wide header's shortcut, and the name of the phone's heart. */
private const val SAVED = "Saved"

/** The tag of the wide header's «HAUL PLUS», for the tests that read it at every desktop width. */
public const val PLUS_PILL_TAG: String = "plus-pill"

/**
 * The phone's header (B-73): what the 1440 header has no room for here — the catalog's every category,
 * «Orders», «HAUL PLUS» — is behind the menu button, left of the logo; no artboard draws one, and the
 * canvas's phone header is otherwise kept as it is drawn. The menu opens whenever somebody follows its
 * entries — over a page still loading or failed too, where the header's links are the shell's (B-67) —
 * and its account row is [onAccount], which the slot itself is not while [pending].
 */
@Composable
private fun CompactHeader(
    header: HaulHeader,
    pending: Boolean,
    account: Modifier,
    onAccount: (() -> Unit)?,
) {
    // The place only (B-72): the canvas's «HELP» had no page behind it.
    Strip(height = 32.dp, padding = 16.dp, size = 10f, spacing = 0.04f) { style ->
        DeliverTo(header.deliverTo, style)
    }
    val menu = LocalHeaderMenu.current?.takeUnless { LocalHaulActions.current == null }
    // On the sign-in page landed on cold the press also asks for the header alone (B-138): the menu opens at once
    // with what the client has, and lists the server's categories once it answers.
    val ask = LocalAskForHeader.current
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 40.dp, height = 44.dp)
                .semantics { contentDescription = OPEN_MENU }
                .pressable(
                    menu?.let {
                        {
                            ask?.invoke()
                            it.open()
                        }
                    },
                ),
            contentAlignment = Alignment.CenterStart,
        ) { Icon(HaulIcons.menu, 24.dp, HaulColors.onSurface) }
        Logo(size = 34f, dot = 9.dp, dotMargin = 2.dp, modifier = Modifier.pressable(LocalLogoAction.current))
        Spacer(Modifier.weight(1f))
        // The phone's shortcuts are icons alone; the wide header's words are their names (B-126).
        Box(
            Modifier.semantics { contentDescription = SAVED }.follows(header.saved).size(44.dp),
            contentAlignment = Alignment.Center,
        ) { Icon(HaulIcons.heart, 24.dp, HaulColors.onSurface) }
        when {
            pending -> {
                Skeleton(Modifier.width(44.dp).height(20.dp), 6.dp)
            }

            header.customerName == null -> {
                Box(account.height(44.dp).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                    Text("Sign in", HaulType.text(15f, 700), softWrap = false)
                }
            }

            else -> {
                LinkMenu(header.accountMenu, alignEnd = true) { press ->
                    Box(
                        press.semantics { contentDescription = header.customerName.orEmpty() }.size(44.dp),
                        contentAlignment = Alignment.Center,
                    ) { Icon(HaulIcons.person, 24.dp, HaulColors.onSurface) }
                }
            }
        }
        CartButton(
            if (pending) 0 else header.cartCount,
            Modifier.padding(start = 4.dp).follows(header.cart),
            height = 44.dp,
            radius = 14.dp,
            horizontal = 12.dp,
            gap = 8.dp,
            label = false,
        )
    }
    val field = LocalSearchField.current
    Box(
        Modifier
            .then(
                if (field !=
                    null
                ) {
                    Modifier.onGloballyPositioned { field.scrimTop = it.boundsInRoot().bottom }
                } else {
                    Modifier
                },
            ).padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
    ) {
        SearchField(
            header,
            Modifier.fillMaxWidth(),
            height = 50.dp,
            radius = 14.dp,
            start = 16.dp,
            end = 3.dp,
            gap = 10.dp,
            textSize = 15f,
            button = 40.dp,
            buttonRadius = 11.dp,
            glyph = 20.dp,
        ) {
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(HaulColors.outlineVariant))
    // The row scrolls sideways (B-73): the canvas cuts the row at the edge, which is the sign that there is more —
    // the row, not always a word: at 390 the canvas's ends on «Fashion» whole (B-152, research «Decided in B-73»).
    Row(
        Modifier
            .fillMaxWidth()
            // `height: 46px` with a 1 px top border outside it: 47 in all.
            .height(46.dp)
            .testTag(CATEGORY_ROW_TAG)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Deals(size = 14f, Modifier.follows(header.deals))
        SpacedWords(header.categories, 22.dp, HaulType.text(14f, 500), links = header.catalog)
    }
    if (menu != null && menu.isOpen) {
        // Over the storefront in its own layer, not a `Dialog` (B-126).
        SheetOverPage(onDismiss = menu::close) {
            HeaderMenuSheet(header, onAccount = onAccount, onClose = menu::close)
        }
    }
}

/** The tag of the phone header's category row, for the tests that scroll it. */
public const val CATEGORY_ROW_TAG: String = "category-row"

@Composable
private fun Strip(
    height: Dp,
    padding: Dp,
    size: Float,
    spacing: Float,
    content: @Composable (TextStyle) -> Unit,
) {
    val style = HaulType.label(size, 500, spacing).copy(color = HaulColors.inverseOnSurface)
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(HaulColors.inverseSurface)
            .padding(horizontal = padding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) { content(style) }
}

/**
 * Where the store delivers, as a fact in the strip's own type (B-72): the canvas set the place in the accent
 * colour, bold, under «Deliver to» — the look of a place picker, and there is no picker; the place is the
 * store's default until a customer's address says otherwise.
 */
@Composable
private fun DeliverTo(
    place: String,
    style: TextStyle,
) {
    Text("DELIVERING TO ${place.uppercase()}", style = style, softWrap = false)
}

/**
 * The search field's category picker (B-72): «All categories» or the category the next search is in
 * (`HaulHeader.scopes`: the top-level ones, and the page's own scope below them, B-119), chosen from a menu
 * under it. The choice is the field's ([SearchInput.scope]) and opens
 * nothing by itself. Without a field to search from, or with no scopes on the wire (the placeholder header),
 * it is drawn as the page's scope and opens nothing — except where the header alone can be asked for
 * ([LocalAskForHeader], the sign-in page landed on cold, B-138): there a press asks, and the menu opens with the
 * scopes once they arrive.
 */
@Composable
private fun ScopePicker(header: HaulHeader) {
    val input = LocalSearchInput.current
    val ask = LocalAskForHeader.current?.takeIf { input != null && header.scopes.isEmpty() }
    val chosen = if (input != null) input.scope else header.scope
    val label = header.scopes.firstOrNull { it.category == chosen }?.label ?: ALL_CATEGORIES
    val entries =
        if (input == null || header.scopes.isEmpty()) {
            emptyList()
        } else {
            listOf(MenuEntry(ALL_CATEGORIES) { input.choose(null) }) +
                header.scopes.map { scope -> MenuEntry(scope.label) { input.choose(scope.category) } }
        }
    Menu(entries, current = label, ask = ask) { press ->
        Row(
            press.testTag(SCOPE_PICKER_TAG),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, HaulType.text(14f, 500), softWrap = false)
            Icon(HaulIcons.chevronDown, 16.dp, HaulColors.onSurface)
        }
    }
}

/** What the search picker reads when the search is in every category. */
public const val ALL_CATEGORIES: String = "All categories"

/** The tag of the header's search picker, for the tests that choose a scope. */
public const val SCOPE_PICKER_TAG: String = "scope-picker"

@Composable
internal fun Logo(
    size: Float,
    dot: Dp,
    dotMargin: Dp,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        Text("Haul", HaulType.display(size, 900, letterSpacing = -0.03f))
        Box(Modifier.padding(start = dotMargin, top = dotMargin).size(dot).background(HaulColors.primary, CircleShape))
    }
}

@Composable
private fun SearchField(
    header: HaulHeader,
    modifier: Modifier,
    height: Dp,
    radius: Dp,
    start: Dp,
    end: Dp,
    gap: Dp,
    textSize: Float,
    button: Dp,
    buttonRadius: Dp,
    glyph: Dp,
    trailing: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    val field = LocalSearchField.current
    Row(
        modifier
            .then(if (field != null) Modifier.onGloballyPositioned { field.bounds = it.boundsInRoot() } else Modifier)
            .height(height)
            .background(HaulColors.surfaceContainerLowest, shape)
            .border(2.dp, HaulColors.onSurface, shape)
            // The 2 px border is inside the box's size (`box-sizing: border-box`) but outside its padding.
            .padding(start = start + 2.dp, end = end + 2.dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val input = LocalSearchInput.current
        if (input != null) {
            EditableQuery(header, input, field, textSize, Modifier.weight(1f))
        } else {
            DrawnQuery(header, field, textSize, Modifier.weight(1f))
        }
        trailing()
        val scope = rememberCoroutineScope()
        Box(
            Modifier
                .testTag(SEARCH_BUTTON_TAG)
                .named(SEARCH)
                .size(button)
                .background(HaulColors.secondaryContainer, RoundedCornerShape(buttonRadius))
                .pressable(
                    input?.let {
                        {
                            // What was typed just before the press may not be in the field yet (B-76, B-83);
                            // Enter needs no wait, it arrives behind the keys it follows.
                            scope.launch {
                                awaitTypedInput()
                                it.submit()
                            }
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) { Icon(HaulIcons.search, glyph, HaulColors.onSurface) }
    }
}

/** The query as the tree has it, or the placeholder; the caret only while the field is focused. */
@Composable
private fun DrawnQuery(
    header: HaulHeader,
    field: SearchFieldState?,
    textSize: Float,
    modifier: Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val query = header.query
        if (query == null) {
            Text(
                header.searchPlaceholder,
                HaulType.text(textSize).copy(color = HaulColors.outlineMuted),
                softWrap = false,
            )
        } else {
            Text(query, HaulType.text(textSize, 500), softWrap = false)
            // The caret only while the shopper is typing (Search_Autocomplete); a page showing its
            // query has none (Search_Results).
            if (field?.focused == true) {
                Box(
                    Modifier
                        .padding(start = 2.dp)
                        .width(2.dp)
                        .height(20.dp)
                        .background(HaulColors.primary),
                )
            }
        }
    }
}

/**
 * The field the shopper types in: it starts from the query the page arrived with, reports its focus
 * to [field] (the suggest panel opens under a focused field) and submits on Enter or the search button.
 */
@Composable
private fun EditableQuery(
    header: HaulHeader,
    input: SearchInput,
    field: SearchFieldState?,
    textSize: Float,
    modifier: Modifier,
) {
    LaunchedEffect(header.query, header.scope) { input.show(header.query.orEmpty(), header.scope) }
    HaulTextField(
        value = input.text,
        onValueChange = input::type,
        modifier =
            modifier
                .testTag(SEARCH_FIELD_TAG)
                .named(header.searchPlaceholder)
                .onFocusChanged { field?.focused = it.isFocused }
                .onPreviewKeyEvent {
                    val down = it.type == KeyEventType.KeyDown
                    when (it.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            if (down) input.submit()
                            true
                        }

                        Key.DirectionDown -> {
                            if (down) input.move(1) else input.choosing
                        }

                        Key.DirectionUp -> {
                            if (down) input.move(-1) else input.choosing
                        }

                        else -> {
                            false
                        }
                    }
                },
        textStyle = HaulType.text(textSize, 500),
        singleLine = true,
        cursorBrush = SolidColor(HaulColors.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { input.submit() }),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (input.text.text.isEmpty()) {
                    Text(
                        header.searchPlaceholder,
                        HaulType.text(textSize).copy(color = HaulColors.outlineMuted),
                        softWrap = false,
                    )
                }
                inner()
            }
        },
    )
}

/** The tag of the header's editable search field, for the tests that type into it. */
public const val SEARCH_FIELD_TAG: String = "search-field"

/** The tag of the header's search button, for the tests that press it. */
public const val SEARCH_BUTTON_TAG: String = "search-button"

@Composable
private fun Shortcut(
    icon: ImageVector,
    label: String,
    weight: Int = 500,
    modifier: Modifier = Modifier,
) {
    Shortcut(icon, modifier) { Text(label, HaulType.text(12f, weight)) }
}

@Composable
private fun Shortcut(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
) {
    Column(
        modifier.width(76.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, 24.dp, HaulColors.onSurface)
        label()
    }
}

@Composable
private fun CartButton(
    count: Int,
    modifier: Modifier,
    height: Dp,
    radius: Dp,
    horizontal: Dp,
    gap: Dp,
    label: Boolean,
) {
    // A cart with nothing in it shows no count (Home_Guest), but the button keeps the count's place (B-75):
    // it is as wide with a count as without, so the first line put in moves nothing around it — not the search
    // field beside it at 1440, not the heart and the account on a phone. Empty, the bag and the word sit in
    // the middle of that width; the count, when it comes, takes its half of the room. Up to 99 fits the place:
    // the badge's padding is 3, not the canvas's 6, so that two digits do, and one is centred as before.
    val room = BADGE + gap
    // The phone's button draws the bag and the count alone: its name is the wide one's words (B-131), and it is that
    // name alone (B-136) — the badge's «3» read as its text too, and the browser's mirror kept that «3» once the cart
    // was emptied.
    val named =
        if (label) {
            Modifier
        } else {
            Modifier.clearAndSetSemantics { contentDescription = if (count > 0) "Cart $count" else "Cart" }
        }
    Row(
        modifier
            .testTag(CART_BUTTON_TAG)
            .then(named)
            .height(height)
            .background(HaulColors.inverseSurface, RoundedCornerShape(radius))
            .padding(horizontal = horizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (count <= 0) Spacer(Modifier.width(room / 2))
        // A line put in shakes the bag, pops the badge and rolls its figure (B-158).
        Icon(HaulIcons.bag, 22.dp, HaulColors.onPrimary, Modifier.wigglesOn(count))
        if (label) {
            Spacer(Modifier.width(gap))
            Text("Cart", HaulType.text(16f, 700).copy(color = HaulColors.onPrimary))
        }
        if (count <= 0) {
            Spacer(Modifier.width(room / 2))
        } else {
            Spacer(Modifier.width(gap))
            Box(
                Modifier
                    .bumpsOn(count)
                    .defaultMinSize(minWidth = BADGE)
                    .height(BADGE)
                    .background(HaulColors.secondaryContainer, RoundedCornerShape(12.dp))
                    .padding(horizontal = 3.dp),
                contentAlignment = Alignment.Center,
            ) { RollingText(count.toString(), HaulType.text(13f, 800)) }
        }
    }
}

/** The tag of the header's cart button, for the tests that hold the header still when the count appears. */
public const val CART_BUTTON_TAG: String = "cart-button"

/** The cart count's place: the badge's height, and its width up to two digits. */
private val BADGE = 24.dp

@Composable
private fun Deals(
    size: Float,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(HaulIcons.bolt, 16.dp, HaulColors.error)
        Text("Deals", HaulType.text(size, 800).copy(color = HaulColors.error))
    }
}

@Composable
internal fun Icon(
    icon: ImageVector,
    size: Dp,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Image(
        rememberVectorPainter(icon),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}
