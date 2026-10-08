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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.HaulType.browserLeading
import io.github.youndie.haul.theme.LocalHaulCompact
import io.github.youndie.haul.ui.HaulButton
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.HaulHeaderView
import io.github.youndie.haul.ui.HaulIcons
import io.github.youndie.haul.ui.Icon
import io.github.youndie.haul.ui.Skeleton
import io.github.youndie.haul.ui.Text
import io.github.youndie.haul.ui.accented
import io.github.youndie.haul.ui.gutter

// The client's own screens (research D2): what is drawn before a tree arrives and after a request for
// one fails. No server tree is involved, so the header is the client's — who is looking is not known
// yet — and the numbers are the artboards' (Home_Loading, Catalog_Loading, Home_Error, Catalog_Error).

/**
 * The header before any tree has arrived: the store's default place and promise, its top-level
 * categories, an account placeholder and no cart count. The server's header replaces it with the tree.
 */
public val SHELL_HEADER: HaulHeader =
    HaulHeader(
        id = "shell-header",
        deliverTo = "Brooklyn, NY 11211",
        deliveryPromise = "Free delivery over $35",
        customerName = null,
        cartCount = 0,
        searchPlaceholder = "Search 2.4 million products",
        categories =
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
            ),
    )

/** Why a screen's tree did not arrive, as the shell words it. */
public enum class ShellFailure(
    public val message: String,
) {
    /** The request never got an answer: no network, or the server is not there. */
    Unreachable("We couldn’t reach Haul. Check your connection and try again."),

    /** The server answered with an error of its own. */
    Server("Something went wrong on our side. Try again in a moment."),
}

/** The page a failed screen leaves: «[subject] didn’t *load*», why, and Retry. */
@Composable
public fun ErrorShell(
    subject: String,
    failure: ShellFailure,
    onRetry: () -> Unit = {},
) {
    val compact = LocalHaulCompact.current
    Column(Modifier.fillMaxWidth()) {
        HaulHeaderView(SHELL_HEADER, pending = true)
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
            Text(
                // The canvas balances the title (`text-wrap: balance`), which keeps «didn’t load» together
                // at both widths; a no-break space keeps it together in a plain greedy break.
                accented("$subject didn’t\u00A0load", "load"),
                Modifier.widthIn(max = 1000.dp),
                style =
                    HaulType
                        .display(
                            if (compact) 56f else 112f,
                            800,
                            letterSpacing = if (compact) -0.01f else -0.03f,
                            lineHeight = 0.9f,
                        ).copy(lineBreak = LineBreak.Simple),
            )
            Text(
                failure.message,
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
        HaulHeaderView(SHELL_HEADER, pending = true)
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
        HaulHeaderView(SHELL_HEADER, pending = true)
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
 * A page whose subject is not there (`404`): the client draws it with the header it already has —
 * who is looking is known — an eyebrow, the title with its accent, why, and the way on.
 */
@Composable
public fun NotFoundShell(
    header: HaulHeader,
    eyebrow: String,
    title: String,
    accent: String,
    text: String,
    actionLabel: String,
    onAction: () -> Unit = {},
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
            HaulButton(
                actionLabel,
                height = if (compact) 56.dp else 64.dp,
                radius = 18.dp,
                fill = HaulColors.primary,
                content = HaulColors.onPrimary,
                icon = HaulIcons.arrowRight,
            )
        }
    }
}

/** A product page before its tree: crumbs, the gallery, the identity and the buy box as placeholders. */
@Composable
public fun ProductLoading() {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    Column(Modifier.fillMaxWidth()) {
        HaulHeaderView(SHELL_HEADER, pending = true)
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
