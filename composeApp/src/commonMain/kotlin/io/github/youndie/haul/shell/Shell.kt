package io.github.youndie.haul.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.feature.checkout.CheckoutHeaderView
import io.github.youndie.haul.feature.identity.SignInActions
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.HaulType.browserLeading
import io.github.youndie.haul.theme.LocalHaulCompact
import io.github.youndie.haul.ui.BalancedText
import io.github.youndie.haul.ui.CheckoutHeader
import io.github.youndie.haul.ui.HaulButton
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.HaulHeaderView
import io.github.youndie.haul.ui.HaulIcons
import io.github.youndie.haul.ui.Icon
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.LocalLogoAction
import io.github.youndie.haul.ui.Skeleton
import io.github.youndie.haul.ui.Text
import io.github.youndie.haul.ui.accented
import io.github.youndie.haul.ui.gutter
import io.github.youndie.haul.ui.pressable
import io.github.youndie.kompot.standard.NavigateAction

// The client's own screens (research D2): what is drawn before a tree arrives and after a request for
// one fails. No server tree is involved, so the header is the client's — who is looking is not known
// yet — and the numbers are the artboards' (Home_Loading, Catalog_Loading, Home_Error, Catalog_Error).

/**
 * The header before any tree has arrived: the store's default place and promise, its top-level
 * categories, an account placeholder and no cart count. The server's header replaces it with the tree.
 *
 * Its controls lead where a guest's header from the server would send them (B-67), by the addresses the
 * server's `Frame` fixes for every shopper: «Deals», the cart, «Orders» and «Saved» (a guest's — the
 * sign-in, returning to them, which sends a customer straight on), the account and «HAUL PLUS» (the
 * sign-in). Which address a category has is the server's to say, and no tree has said it yet: each word
 * of the category row, and each entry of «Catalog», opens the catalog's root ([CATALOG]), every
 * top-level category, one press from the one meant. Once a tree has been drawn the shell draws its
 * header instead ([LocalShellHeader]).
 */
public val SHELL_HEADER: HaulHeader =
    run {
        val categories =
            listOf(
                "Electronics",
                "Home & Kitchen",
                "Fashion",
                "Beauty",
                "Kids & Toys",
                "Sports",
                "Grocery",
                "Auto",
                "Books",
                "Pets",
            )
        HaulHeader(
            id = "shell-header",
            deliverTo = "Brooklyn, NY 11211",
            deliveryPromise = "Free delivery over $35",
            customerName = null,
            cartCount = 0,
            searchPlaceholder = "Search 2.4 million products",
            categories = categories,
            account = NavigateAction(SignInActions.SIGN_IN),
            catalog = categories.map { Link(it, NavigateAction(CATALOG)) },
            deals = NavigateAction(DEALS),
            cart = NavigateAction(CART),
            orders = NavigateAction(SignInActions.returningTo(ORDERS)),
            saved = NavigateAction(SignInActions.returningTo(SAVED)),
            plus = NavigateAction(SignInActions.SIGN_IN),
        )
    }

// The server's addresses (`Frame` on the server) the shell's own header follows before a tree names them.
private const val CATALOG = "/c"
private const val DEALS = "/deals"
private const val CART = "/cart"
private const val ORDERS = "/account/orders"
private const val SAVED = "/account/saved"

/**
 * The header the shell draws over a page it draws itself — a placeholder, an error, a page that is not
 * there, the sign-in's (B-67): the header of the last tree drawn, whose links are the server's, or
 * [SHELL_HEADER] before any. Outside the storefront — a screenshot — it is [SHELL_HEADER].
 */
public val LocalShellHeader: ProvidableCompositionLocal<HaulHeader> = staticCompositionLocalOf { SHELL_HEADER }

/**
 * [LocalShellHeader] as a page still loading or failed draws it: who is looking is not shown — the
 * account slot a placeholder, no cart count — and the search field holds [query], a search's.
 */
@Composable
internal fun PendingHeader(query: String? = null) {
    HaulHeaderView(LocalShellHeader.current.copy(query = query), pending = true)
}

/** Why a screen's tree did not arrive, as the shell words it. */
public enum class ShellFailure(
    public val message: String,
) {
    /** The request never got an answer: no network, or the server is not there. */
    Unreachable("We couldn’t reach Haul. Check your connection and try again."),

    /** The server answered with an error of its own. */
    Server("Something went wrong on our side. Try again in a moment."),
}

/**
 * The page a failed screen leaves: «[subject] didn’t *load*», why, and Retry. A search's
 * ([SearchError]) keeps the query in the header's field and says «Search didn’t *respond*».
 */
@Composable
public fun ErrorShell(
    subject: String,
    failure: ShellFailure,
    onRetry: () -> Unit = {},
) {
    ErrorPage({
        PendingHeader()
    }, accented("$subject didn’t\u00A0load", "load"), failure.message, balanced = false, onRetry)
}

/** A failed search (Search_Error): the query stays in the field, and the shopper is told so. */
@Composable
public fun SearchError(
    query: String,
    onRetry: () -> Unit = {},
) {
    ErrorPage(
        { PendingHeader(query) },
        accented("Search didn’t respond", "respond"),
        "Your query is still in the field. Try again in a moment.",
        // `text-wrap: balance` keeps «didn’t respond» together at 1440 and breaks it on a phone,
        // where the two words do not fit one line.
        balanced = true,
        onRetry = onRetry,
    )
}

@Composable
private fun ErrorPage(
    header: @Composable () -> Unit,
    title: AnnotatedString,
    message: String,
    balanced: Boolean,
    onRetry: () -> Unit,
) {
    val compact = LocalHaulCompact.current
    Column(Modifier.fillMaxWidth()) {
        header()
        Column(
            Modifier.padding(
                start = gutter(),
                end = gutter(),
                top = if (compact) 56.dp else 120.dp,
                bottom = if (compact) 96.dp else 200.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 20.dp else 28.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(HaulIcons.alert, 18.dp, HaulColors.error)
                Text("Something went wrong".uppercase(), HaulType.label(11f, 600, 0.08f).copy(color = HaulColors.error))
            }
            val style =
                HaulType
                    .display(
                        if (compact) 56f else 112f,
                        800,
                        letterSpacing = if (compact) -0.01f else -0.03f,
                        lineHeight = 0.9f,
                    ).copy(lineBreak = LineBreak.Simple)
            if (balanced) {
                BalancedText(title, style, Modifier.widthIn(max = 1000.dp))
            } else {
                // The canvas balances the title (`text-wrap: balance`), which keeps «didn’t load» together
                // at both widths; a no-break space keeps it together in a plain greedy break.
                Text(title, Modifier.widthIn(max = 1000.dp), style = style)
            }
            Text(
                message,
                HaulType
                    .text(
                        if (compact) 16f else 18f,
                        lineHeight = 1.5f,
                    ).browserLeading()
                    .copy(color = HaulColors.onSurfaceVariant),
                Modifier.widthIn(max = 560.dp),
            )
            HaulButton(
                "Retry",
                height = if (compact) 56.dp else 64.dp,
                radius = 18.dp,
                fill = HaulColors.primary,
                content = HaulColors.onPrimary,
                icon = HaulIcons.retry,
                iconFirst = true,
                onClick = onRetry,
            )
        }
    }
}

/** The home page before its tree: the campaign blocks, the category tiles and two product rows as placeholders. */
@Composable
public fun HomeLoading() {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    Column(Modifier.fillMaxWidth()) {
        PendingHeader()
        if (compact) {
            Column(
                Modifier.padding(start = gutter, end = gutter, top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Skeleton(Modifier.fillMaxWidth().height(440.dp), 24.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(2) { Skeleton(Modifier.weight(1f).height(200.dp), 24.dp) }
                }
            }
        } else {
            Row(
                Modifier.padding(start = gutter, end = gutter, top = 28.dp).height(560.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Skeleton(Modifier.weight(2f).fillMaxHeight(), 28.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(2) { Skeleton(Modifier.fillMaxWidth().weight(1f), 28.dp) }
                }
            }
        }
        LoadingSection(if (compact) 220.dp else 460.dp) {
            Grid(
                columns = if (compact) 4 else 8,
                count = 8,
                columnGap = if (compact) 10.dp else 16.dp,
                rowGap = 16.dp,
            ) {
                Column(it, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Skeleton(Modifier.fillMaxWidth().aspectRatio(1f), if (compact) 16.dp else 22.dp)
                    Skeleton(Modifier.fillMaxWidth(0.7f).height(14.dp), 5.dp)
                }
            }
        }
        LoadingSection(if (compact) 240.dp else 520.dp) { ProductRow() }
        LoadingSection(if (compact) 200.dp else 420.dp) { ProductRow() }
        Spacer(Modifier.height(if (compact) 64.dp else 80.dp))
    }
}

@Composable
private fun LoadingSection(
    title: Dp,
    content: @Composable () -> Unit,
) {
    val compact = LocalHaulCompact.current
    Column(Modifier.fillMaxWidth().padding(top = if (compact) 48.dp else 72.dp)) {
        Skeleton(
            Modifier
                .padding(start = gutter(), bottom = if (compact) 18.dp else 28.dp)
                .size(title, if (compact) 34.dp else 56.dp),
            10.dp,
        )
        content()
    }
}

/** Six product placeholders: a row of six at 1440, a row of 160-wide ones running off the edge on a phone. */
@Composable
private fun ProductRow() {
    if (LocalHaulCompact.current) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = gutter()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(6) { CardSkeleton(Modifier.width(160.dp)) }
        }
    } else {
        Box(Modifier.padding(horizontal = gutter())) {
            Grid(columns = 6, count = 6, columnGap = 24.dp, rowGap = 0.dp) { CardSkeleton(it) }
        }
    }
}

@Composable
private fun CardSkeleton(modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Skeleton(Modifier.fillMaxWidth().aspectRatio(1f), 18.dp)
        Skeleton(Modifier.fillMaxWidth(0.45f).height(22.dp), 6.dp)
        Skeleton(Modifier.fillMaxWidth().height(14.dp), 5.dp)
        Skeleton(Modifier.fillMaxWidth(0.7f).height(14.dp), 5.dp)
        Skeleton(Modifier.fillMaxWidth(0.35f).height(12.dp), 5.dp)
    }
}

@Composable
private fun Grid(
    columns: Int,
    count: Int,
    columnGap: Dp,
    rowGap: Dp,
    modifier: Modifier = Modifier,
    cell: @Composable (Modifier) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(rowGap)) {
        (0 until count).chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(columnGap)) {
                row.forEach { _ -> cell(Modifier.weight(1f)) }
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** A category page before its tree: crumbs, title, kinds, the facet column, the chips and twelve cards as placeholders. */
@Composable
public fun CatalogLoading() {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    Column(Modifier.fillMaxWidth()) {
        PendingHeader()
        Column(
            Modifier.padding(
                start = gutter,
                end = gutter,
                top = if (compact) 20.dp else 28.dp,
                bottom = if (compact) 64.dp else 80.dp,
            ),
        ) {
            Skeleton(Modifier.size(if (compact) 240.dp else 320.dp, 12.dp), 5.dp)
            Skeleton(
                Modifier
                    .padding(top = if (compact) 16.dp else 20.dp, bottom = if (compact) 22.dp else 26.dp)
                    .size(if (compact) 260.dp else 620.dp, if (compact) 50.dp else 96.dp),
                12.dp,
            )
            Row(
                Modifier
                    .padding(bottom = if (compact) 16.dp else 36.dp)
                    .fillMaxWidth()
                    .clipToBounds()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                KIND_WIDTHS.forEach { Skeleton(Modifier.width(it.dp).height(if (compact) 40.dp else 44.dp), 22.dp) }
            }
            if (compact) {
                Row(
                    Modifier.padding(bottom = 24.dp).wrapContentWidth(Alignment.Start, unbounded = true),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Two halves and a gap: the canvas lets the second run 10 px into the gutter.
                    repeat(2) { Skeleton(Modifier.width(179.dp).height(48.dp), 12.dp) }
                }
                Grid(columns = 2, count = 12, columnGap = 12.dp, rowGap = 28.dp) { CardSkeleton(it) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    FacetSkeleton()
                    Column(Modifier.weight(1f)) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 28.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            listOf(80, 80, 110, 150).forEach { Skeleton(Modifier.size(it.dp, 38.dp), 19.dp) }
                            Spacer(Modifier.weight(1f))
                            Skeleton(Modifier.size(150.dp, 44.dp), 12.dp)
                        }
                        Grid(columns = 4, count = 12, columnGap = 24.dp, rowGap = 40.dp) { CardSkeleton(it) }
                    }
                }
            }
        }
    }
}

private val KIND_WIDTHS = listOf(60, 96, 80, 120, 84, 72, 64)

/** The facet column's placeholder: a title bar and value bars per block, hairlines between. */
@Composable
private fun FacetSkeleton() {
    Column(Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FACET_BLOCKS.forEachIndexed { index, (title, rows) ->
            if (index >
                0
            ) {
                Box(
                    Modifier
                        .padding(
                            vertical = 12.dp,
                        ).fillMaxWidth()
                        .height(1.dp)
                        .background(HaulColors.outlineVariant),
                )
            }
            Skeleton(Modifier.size(title.dp, 16.dp), 6.dp)
            rows.forEach { Skeleton(Modifier.size(it.dp, 18.dp), 6.dp) }
        }
    }
}

private val FACET_BLOCKS =
    listOf(
        60 to listOf(280),
        70 to listOf(240, 240, 240, 240),
        80 to listOf(240, 240),
        70 to listOf(200, 200),
    )

/**
 * A page whose subject is not there (`404`): the client draws it with the header it already has — the
 * last tree's, or [SHELL_HEADER] when the shopper landed on it (B-67) — an eyebrow, the title with its
 * accent, why, and the way on. The sign-in's
 * pages are drawn the same way (B-44, B-66): no [actionLabel] while there is nothing to press, and a
 * second way on, [secondaryLabel], as a link under the button.
 */
@Composable
public fun NotFoundShell(
    header: HaulHeader,
    eyebrow: String,
    title: String,
    accent: String,
    text: String,
    actionLabel: String?,
    onAction: () -> Unit = {},
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
) {
    val compact = LocalHaulCompact.current
    Column(Modifier.fillMaxWidth()) {
        HaulHeaderView(header)
        Column(
            Modifier.padding(
                start = gutter(),
                end = gutter(),
                top = if (compact) 56.dp else 120.dp,
                bottom = if (compact) 96.dp else 160.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 18.dp else 24.dp),
        ) {
            Text(eyebrow.uppercase(), HaulType.label(11f, 600, 0.08f).copy(color = HaulColors.outline))
            Text(
                accented(title, accent),
                Modifier.widthIn(max = 900.dp),
                style =
                    HaulType
                        .display(
                            if (compact) 44f else 84f,
                            800,
                            letterSpacing = if (compact) -0.01f else -0.03f,
                            lineHeight = 0.95f,
                        ).copy(lineBreak = LineBreak.Simple),
            )
            Text(
                text,
                HaulType.text(if (compact) 16f else 18f, lineHeight = 1.5f).copy(color = HaulColors.onSurfaceVariant),
                Modifier.widthIn(max = 560.dp),
            )
            if (actionLabel != null) {
                HaulButton(
                    actionLabel,
                    height = if (compact) 56.dp else 64.dp,
                    radius = 18.dp,
                    fill = HaulColors.primary,
                    content = HaulColors.onPrimary,
                    icon = HaulIcons.arrowRight,
                    onClick = onAction,
                )
            }
            if (secondaryLabel != null) {
                Text(
                    secondaryLabel,
                    HaulType.text(if (compact) 15f else 16f, 600).copy(textDecoration = TextDecoration.Underline),
                    Modifier.pressable(onSecondary).padding(vertical = 6.dp),
                )
            }
        }
    }
}

/** A product page before its tree: crumbs, the gallery, the identity and the buy box as placeholders. */
@Composable
public fun ProductLoading() {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    Column(Modifier.fillMaxWidth()) {
        PendingHeader()
        Column(
            Modifier.padding(
                start = gutter,
                end = gutter,
                top = if (compact) 20.dp else 28.dp,
                bottom = if (compact) 64.dp else 80.dp,
            ),
        ) {
            Skeleton(
                Modifier.padding(bottom = if (compact) 16.dp else 28.dp).size(if (compact) 220.dp else 420.dp, 12.dp),
                5.dp,
            )
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    Skeleton(Modifier.fillMaxWidth().aspectRatio(1f), 24.dp)
                    IdentitySkeleton(titleHeight = 34.dp)
                    BuyBoxSkeleton(Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    Row(Modifier.weight(1.3f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.width(76.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            repeat(5) { Skeleton(Modifier.fillMaxWidth().aspectRatio(1f), 14.dp) }
                        }
                        Skeleton(Modifier.weight(1f).aspectRatio(1f), 28.dp)
                    }
                    Box(Modifier.weight(1f)) { IdentitySkeleton(titleHeight = 48.dp) }
                    BuyBoxSkeleton(Modifier.width(384.dp))
                }
            }
        }
    }
}

@Composable
private fun IdentitySkeleton(titleHeight: Dp) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Skeleton(Modifier.size(60.dp, 13.dp), 5.dp)
        Skeleton(Modifier.fillMaxWidth().height(titleHeight), 10.dp)
        Skeleton(Modifier.fillMaxWidth(0.7f).height(titleHeight), 10.dp)
        Skeleton(Modifier.size(220.dp, 16.dp), 6.dp)
        Skeleton(Modifier.size(160.dp, 12.dp), 5.dp)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) { Skeleton(Modifier.size(64.dp), 14.dp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(150, 120, 120).forEach { Skeleton(Modifier.size(it.dp, 48.dp), 12.dp) }
        }
    }
}

@Composable
private fun BuyBoxSkeleton(modifier: Modifier) {
    Column(
        modifier
            .background(
                HaulColors.surfaceContainerLowest,
                androidx.compose.foundation.shape
                    .RoundedCornerShape(28.dp),
            ).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Skeleton(Modifier.size(180.dp, 64.dp), 10.dp)
        Skeleton(Modifier.size(240.dp, 14.dp), 5.dp)
        Skeleton(Modifier.fillMaxWidth().height(64.dp), 18.dp)
        Skeleton(Modifier.fillMaxWidth().height(56.dp), 18.dp)
        Skeleton(Modifier.fillMaxWidth().height(14.dp), 5.dp)
        Skeleton(Modifier.fillMaxWidth(0.8f).height(14.dp), 5.dp)
        Skeleton(Modifier.fillMaxWidth().height(14.dp), 5.dp)
        Skeleton(Modifier.fillMaxWidth().height(80.dp), 18.dp)
    }
}

/**
 * A search before its tree (Search_Loading): the header already holds the query; a count, the title,
 * six category chips and ten cards as placeholders — five across at 1440, two on a phone.
 */
@Composable
public fun SearchLoading(query: String) {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    Column(Modifier.fillMaxWidth()) {
        PendingHeader(query)
        Column(
            Modifier.padding(
                start = gutter,
                end = gutter,
                top = if (compact) 24.dp else 36.dp,
                bottom = if (compact) 64.dp else 80.dp,
            ),
        ) {
            Skeleton(Modifier.size(140.dp, 13.dp), 5.dp)
            Skeleton(
                Modifier
                    .padding(top = 16.dp, bottom = if (compact) 20.dp else 26.dp)
                    .size(if (compact) 300.dp else 720.dp, if (compact) 50.dp else 96.dp),
                12.dp,
            )
            Row(
                Modifier
                    .padding(bottom = if (compact) 24.dp else 32.dp)
                    .fillMaxWidth()
                    .clipToBounds()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SEARCH_CHIP_WIDTHS.forEach {
                    Skeleton(
                        Modifier.width(it.dp).height(if (compact) 40.dp else 44.dp),
                        22.dp,
                    )
                }
            }
            if (compact) {
                Grid(columns = 2, count = 10, columnGap = 12.dp, rowGap = 28.dp) { CardSkeleton(it) }
            } else {
                Grid(columns = 5, count = 10, columnGap = 24.dp, rowGap = 40.dp) { CardSkeleton(it) }
            }
        }
    }
}

private val SEARCH_CHIP_WIDTHS = listOf(100, 140, 150, 130, 100, 90)

/** A failed cart (Cart_Error): nothing in it was lost, which is what the shopper is told. */
@Composable
public fun CartError(onRetry: () -> Unit = {}) {
    ErrorPage(
        { PendingHeader() },
        accented("Your cart didn’t load", "load"),
        "Nothing in it was lost. Try again in a moment.",
        // `text-wrap: balance`: «Your cart / didn’t load» at both widths.
        balanced = true,
        onRetry = onRetry,
    )
}

/**
 * The cart before its tree (Cart_Loading): the title, two seller groups — one line, then two — and the
 * summary as placeholders; the summary beside the groups at 1440, under them on a phone.
 */
@Composable
public fun CartLoading() {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    Column(Modifier.fillMaxWidth()) {
        PendingHeader()
        Column(
            Modifier.padding(
                start = gutter,
                end = gutter,
                top = if (compact) 24.dp else 40.dp,
                bottom = if (compact) 64.dp else 96.dp,
            ),
        ) {
            Skeleton(
                Modifier
                    .padding(bottom = if (compact) 24.dp else 36.dp)
                    .size(if (compact) 140.dp else 260.dp, if (compact) 50.dp else 96.dp),
                12.dp,
            )
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CartGroupSkeleton(lines = 1)
                    CartGroupSkeleton(lines = 2)
                    CartSummarySkeleton(Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CartGroupSkeleton(lines = 1)
                        CartGroupSkeleton(lines = 2)
                    }
                    CartSummarySkeleton(Modifier.width(420.dp))
                }
            }
        }
    }
}

@Composable
private fun CartGroupSkeleton(lines: Int) {
    val compact = LocalHaulCompact.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(24.dp))
            .padding(horizontal = if (compact) 16.dp else 28.dp, vertical = if (compact) 16.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Skeleton(Modifier.size(220.dp, 16.dp), 6.dp)
        repeat(lines) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Skeleton(Modifier.size(22.dp), 6.dp)
                Skeleton(Modifier.size(if (compact) 88.dp else 128.dp), 16.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Skeleton(Modifier.fillMaxWidth(0.8f).height(16.dp), 6.dp)
                    Skeleton(Modifier.fillMaxWidth(0.5f).height(14.dp), 5.dp)
                }
                if (!compact) {
                    Skeleton(Modifier.size(112.dp, 48.dp), 14.dp)
                    Skeleton(Modifier.size(90.dp, 30.dp), 8.dp)
                }
            }
        }
    }
}

@Composable
private fun CartSummarySkeleton(modifier: Modifier) {
    Column(
        modifier
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(28.dp))
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Skeleton(Modifier.size(220.dp, 30.dp), 8.dp)
        repeat(3) { Skeleton(Modifier.fillMaxWidth().height(14.dp), 5.dp) }
        Skeleton(Modifier.align(Alignment.End).size(160.dp, 52.dp), 10.dp)
        Skeleton(Modifier.fillMaxWidth().height(52.dp), 14.dp)
        Skeleton(Modifier.fillMaxWidth().height(64.dp), 18.dp)
    }
}

/**
 * The checkout's header before its tree (Checkout_Loading, Checkout_Error): the client's own copy of
 * what the server's tree draws, the first step current.
 */
public val CHECKOUT_SHELL_HEADER: CheckoutHeader =
    CheckoutHeader(
        id = "checkout-shell-header",
        steps = listOf("Delivery", "Payment", "Review"),
        current = 0,
        secureLabel = "Secure checkout",
    )

/** A failed checkout (Checkout_Error): the cart is as it was, which is what the shopper is told. */
@Composable
public fun CheckoutError(onRetry: () -> Unit = {}) {
    ErrorPage(
        { CheckoutHeaderView(CHECKOUT_SHELL_HEADER, onHome = LocalLogoAction.current) },
        accented("Checkout didn’t load", "load"),
        "Your cart is unchanged. Try again in a moment.",
        // `text-wrap: balance`: «Checkout / didn’t load» at both widths.
        balanced = true,
        onRetry = onRetry,
    )
}

/**
 * The checkout before its tree (Checkout_Loading): the title, four sections and the order as
 * placeholders; the order beside the sections at 1440, under them on a phone.
 */
@Composable
public fun CheckoutLoading() {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    Column(Modifier.fillMaxWidth()) {
        CheckoutHeaderView(CHECKOUT_SHELL_HEADER, onHome = LocalLogoAction.current)
        Column(
            Modifier.padding(
                start = gutter,
                end = gutter,
                top = if (compact) 24.dp else 40.dp,
                bottom = if (compact) 64.dp else 96.dp,
            ),
        ) {
            Skeleton(
                Modifier
                    .padding(bottom = if (compact) 24.dp else 40.dp)
                    .size(if (compact) 240.dp else 520.dp, if (compact) 50.dp else 96.dp),
                12.dp,
            )
            val sections: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 20.dp)) {
                    (if (compact) listOf(300, 330, 170, 280) else listOf(110, 220, 150, 110)).forEach {
                        CheckoutSectionSkeleton(it.dp)
                    }
                }
            }
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    sections(Modifier.fillMaxWidth())
                    CheckoutSummarySkeleton(Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    sections(Modifier.weight(1f))
                    CheckoutSummarySkeleton(Modifier.width(420.dp))
                }
            }
        }
    }
}

@Composable
private fun CheckoutSectionSkeleton(block: Dp) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(24.dp))
            .padding(if (LocalHaulCompact.current) 20.dp else 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Skeleton(Modifier.size(240.dp, 30.dp), 8.dp)
        Skeleton(Modifier.fillMaxWidth().height(block), 18.dp)
    }
}

@Composable
private fun CheckoutSummarySkeleton(modifier: Modifier) {
    Column(
        modifier
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(28.dp))
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Skeleton(Modifier.size(200.dp, 30.dp), 8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { repeat(3) { Skeleton(Modifier.size(72.dp), 14.dp) } }
        repeat(3) { Skeleton(Modifier.fillMaxWidth().height(14.dp), 5.dp) }
        Skeleton(Modifier.align(Alignment.End).size(160.dp, 52.dp), 10.dp)
        Skeleton(Modifier.fillMaxWidth().height(64.dp), 18.dp)
    }
}

/**
 * An order's page before its tree (Order_Loading): the crumbs, the meta line and the title, the steps,
 * two shipments of two lines and the summary as placeholders; the summary beside the column at 1440,
 * under it on a phone.
 */
@Composable
public fun OrderLoading() {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    Column(Modifier.fillMaxWidth()) {
        PendingHeader()
        Column(
            Modifier.padding(
                start = gutter,
                end = gutter,
                top = if (compact) 24.dp else 40.dp,
                bottom = if (compact) 64.dp else 96.dp,
            ),
        ) {
            Skeleton(Modifier.size(260.dp, 12.dp), 5.dp)
            Skeleton(Modifier.padding(top = 20.dp).size(240.dp, 12.dp), 5.dp)
            Skeleton(
                Modifier
                    .padding(top = 16.dp, bottom = if (compact) 28.dp else 40.dp)
                    .size(if (compact) 300.dp else 760.dp, if (compact) 50.dp else 96.dp),
                12.dp,
            )
            val column: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OrderStepsSkeleton()
                    repeat(2) { OrderShipmentSkeleton() }
                }
            }
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    column(Modifier.fillMaxWidth())
                    OrderSummarySkeleton(Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    column(Modifier.weight(1f))
                    OrderSummarySkeleton(Modifier.width(420.dp))
                }
            }
        }
    }
}

@Composable
private fun OrderStepsSkeleton() {
    val compact = LocalHaulCompact.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(24.dp))
            .padding(horizontal = if (compact) 20.dp else 32.dp, vertical = if (compact) 22.dp else 28.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(4) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Skeleton(Modifier.fillMaxWidth().height(8.dp), 4.dp)
                Skeleton(Modifier.fillMaxWidth(0.6f).height(12.dp), 5.dp)
            }
        }
    }
}

@Composable
private fun OrderShipmentSkeleton() {
    val compact = LocalHaulCompact.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(24.dp))
            .padding(if (compact) 20.dp else 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Skeleton(Modifier.size(240.dp, 16.dp), 6.dp)
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Skeleton(Modifier.size(if (compact) 72.dp else 88.dp), 14.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Skeleton(Modifier.fillMaxWidth(0.8f).height(16.dp), 6.dp)
                    Skeleton(Modifier.fillMaxWidth(0.45f).height(14.dp), 5.dp)
                }
            }
        }
    }
}

@Composable
private fun OrderSummarySkeleton(modifier: Modifier) {
    Column(
        modifier
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(28.dp))
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Skeleton(Modifier.size(180.dp, 30.dp), 8.dp)
        repeat(3) { Skeleton(Modifier.fillMaxWidth().height(14.dp), 5.dp) }
        Skeleton(Modifier.align(Alignment.End).size(140.dp, 44.dp), 10.dp)
        Skeleton(Modifier.fillMaxWidth().height(60.dp), 14.dp)
    }
}

/** A failed account (Account_Error): «Your account didn’t *load*», and Retry. */
@Composable
public fun AccountError(onRetry: () -> Unit = {}) {
    ErrorPage(
        { PendingHeader() },
        accented("Your account didn’t load", "load"),
        ShellFailure.Server.message,
        // `text-wrap: balance`: «Your account / didn’t load» at both widths.
        balanced = true,
        onRetry = onRetry,
    )
}

/**
 * The account before its tree (Account_Loading) — the overview's and the orders' alike: the profile and
 * the menu (on a phone, the profile row and three tabs), the title, three tiles and two order cards as
 * placeholders.
 */
@Composable
public fun AccountLoading() {
    val compact = LocalHaulCompact.current
    AccountPageLoading { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 32.dp else 40.dp)) {
            Skeleton(Modifier.size(if (compact) 200.dp else 420.dp, if (compact) 50.dp else 96.dp), 12.dp)
            if (compact) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) { repeat(3) { AccountTileSkeleton(Modifier.fillMaxWidth()) } }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) { repeat(3) { AccountTileSkeleton(Modifier.weight(1f)) } }
            }
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Skeleton(Modifier.size(240.dp, 36.dp), 8.dp)
                repeat(2) { AccountOrderSkeleton() }
            }
        }
    }
}

/**
 * The Saved list before its tree (Saved_Loading, B-20): the account's profile and menu, the title, the two
 * chips and twelve cards as placeholders — four to a row at 1440, two on a phone.
 */
@Composable
public fun SavedLoading() {
    val compact = LocalHaulCompact.current
    AccountPageLoading { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 32.dp else 40.dp)) {
            Skeleton(Modifier.size(if (compact) 180.dp else 320.dp, if (compact) 50.dp else 96.dp), 12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(90, 170).forEach { Skeleton(Modifier.size(it.dp, 44.dp), 22.dp) }
            }
            if (compact) {
                Grid(columns = 2, count = 12, columnGap = 12.dp, rowGap = 28.dp) { CardSkeleton(it) }
            } else {
                Grid(columns = 4, count = 12, columnGap = 24.dp, rowGap = 40.dp) { CardSkeleton(it) }
            }
        }
    }
}

/** A failed Saved list (Saved_Error): «Saved didn’t *load*», and Retry. */
@Composable
public fun SavedError(onRetry: () -> Unit = {}) {
    ErrorPage(
        { PendingHeader() },
        accented("Saved didn’t load", "load"),
        ShellFailure.Server.message,
        // `text-wrap: balance`: one line at 1440, «Saved / didn’t load» on a phone.
        balanced = true,
        onRetry = onRetry,
    )
}

/**
 * An account page before its tree: the pending header, the profile and the menu — on a phone the profile
 * row and three tabs above the page — and the page's own placeholders, [main].
 */
@Composable
private fun AccountPageLoading(main: @Composable (Modifier) -> Unit) {
    val compact = LocalHaulCompact.current
    Column(Modifier.fillMaxWidth()) {
        PendingHeader()
        if (compact) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 64.dp)) {
                Column(Modifier.padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AccountProfileSkeleton(avatar = 48.dp, name = 14.dp, subtitle = 100.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(100, 80, 80).forEach { Skeleton(Modifier.size(it.dp, 44.dp), 22.dp) }
                    }
                }
                main(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.padding(start = 48.dp, end = 48.dp, top = 40.dp, bottom = 96.dp)) {
                Column(Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    AccountProfileSkeleton(avatar = 64.dp, name = 16.dp, subtitle = 110.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Skeleton(Modifier.fillMaxWidth().height(48.dp), 14.dp)
                        Skeleton(
                            Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth(0.7f).height(20.dp),
                            6.dp,
                        )
                        Skeleton(Modifier.padding(horizontal = 16.dp).fillMaxWidth(0.6f).height(20.dp), 6.dp)
                    }
                }
                Spacer(Modifier.width(48.dp))
                main(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AccountProfileSkeleton(
    avatar: Dp,
    name: Dp,
    subtitle: Dp,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (avatar < 64.dp) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Skeleton(Modifier.size(avatar), avatar / 2)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Skeleton(Modifier.size(140.dp, name), if (name > 14.dp) 6.dp else 5.dp)
            Skeleton(Modifier.size(subtitle, 12.dp), 5.dp)
        }
    }
}

@Composable
private fun AccountTileSkeleton(modifier: Modifier) {
    Column(
        modifier
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(24.dp))
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Skeleton(Modifier.size(80.dp, 11.dp), 4.dp)
        Skeleton(Modifier.size(140.dp, 52.dp), 10.dp)
        Skeleton(Modifier.fillMaxWidth(0.8f).height(14.dp), 5.dp)
    }
}

@Composable
private fun AccountOrderSkeleton() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(24.dp))
            .padding(horizontal = 32.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Skeleton(Modifier.size(260.dp, 12.dp), 5.dp)
        Skeleton(Modifier.fillMaxWidth(0.7f).height(32.dp), 8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) { Skeleton(Modifier.weight(1f).height(8.dp), 4.dp) }
        }
    }
}
