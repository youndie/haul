package io.github.youndie.haul.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.LocalHaulCompact

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
 * text to [onSubmit], which opens the results. Without one — a screenshot — the field is drawn, not edited.
 */
@Stable
public class SearchInput(
    private val onSubmit: (String) -> Unit,
) {
    public var text: TextFieldValue by mutableStateOf(TextFieldValue())
        private set

    /** Whether [text] is the shopper's own typing, rather than the query the page arrived with. */
    public var typed: Boolean by mutableStateOf(false)
        private set

    /** The shopper changed the text (or only moved the caret). */
    public fun type(value: TextFieldValue) {
        if (value.text != text.text) typed = true
        text = value
    }

    /** A page arrived holding [query] (none outside search): the field shows it, caret at its end. */
    public fun show(query: String) {
        text = TextFieldValue(query, TextRange(query.length))
        typed = false
    }

    public fun submit() {
        onSubmit(text.text)
    }
}

/** The header's search field as the shopper edits it; none draws the field as the tree has it. */
public val LocalSearchInput: ProvidableCompositionLocal<SearchInput?> = staticCompositionLocalOf { null }

/** Where pressing the logo goes — the home page, which is the app's own; none leaves the logo inert. */
public val LocalLogoAction: ProvidableCompositionLocal<(() -> Unit)?> = staticCompositionLocalOf { null }

/**
 * The header, at the width the page is drawn at (`HaulHeader` on the wire). [pending] is the client's
 * own header before any tree has arrived (Loading, Error): who is looking is not known yet, so the
 * account slot is a placeholder and the cart has no count. [onAccount] is a tap on the account slot —
 * «Sign in» for a guest, the name for a customer — and the renderer hands it `HaulHeader.account`.
 */
@Composable
public fun HaulHeaderView(
    header: HaulHeader,
    modifier: Modifier = Modifier,
    pending: Boolean = false,
    onAccount: (() -> Unit)? = null,
) {
    val account = accountTap(if (pending) null else onAccount, header.customerName)
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
        if (compact) CompactHeader(header, pending, account) else WideHeader(header, pending, account)
        Box(Modifier.fillMaxWidth().height(1.dp).background(HaulColors.outlineVariant))
    }
}

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
        Modifier.clickable(
            interactionSource = null,
            indication = null,
            onClickLabel = if (customerName == null) "Sign in" else "Account",
            role = Role.Button,
            onClick = onAccount,
        )
    }

@Composable
private fun WideHeader(
    header: HaulHeader,
    pending: Boolean,
    account: Modifier,
) {
    Strip(height = 36.dp, padding = 48.dp, size = 11f, spacing = 0.06f) { style ->
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            DeliverTo(header.deliverTo, style)
            Text(header.deliveryPromise.uppercase(), style)
        }
        // No page exists for these yet (B-49): plain text, nothing to press.
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            listOf("Sell on HAUL", "Help", "EN · USD").forEach { Text(it.uppercase(), style) }
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
        SearchField(
            header,
            Modifier.weight(1f),
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("All categories", HaulType.text(14f, 500))
                Icon(HaulIcons.chevronDown, 16.dp, HaulColors.onSurface)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            // «Orders» goes where the tree says (`HaulHeader.orders`): a customer's orders, a guest's sign-in.
            Shortcut(HaulIcons.box, "Orders", modifier = Modifier.follows(header.orders))
            // «Saved» likewise (`HaulHeader.saved`, B-20): a customer's Saved list, a guest's sign-in.
            Shortcut(HaulIcons.heart, "Saved", modifier = Modifier.follows(header.saved))
            when {
                pending -> Shortcut(HaulIcons.person) { Skeleton(Modifier.width(40.dp).height(10.dp), 5.dp) }
                header.customerName == null -> Shortcut(HaulIcons.person, "Sign in", weight = 700, modifier = account)
                else -> Shortcut(HaulIcons.person, header.customerName.orEmpty(), modifier = account)
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
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Deals(size = 15f, Modifier.follows(header.deals))
        SpacedWords(header.categories, 28.dp, HaulType.text(15f, 500), links = header.catalog)
        Spacer(Modifier.weight(1f))
        // The Plus offer (`HaulHeader.plus`, B-49): the trial's dialog, a member's account, a guest's sign-in.
        Text(
            "HAUL PLUS",
            HaulType.label(11f, 600, 0.06f),
            Modifier
                .follows(header.plus)
                .background(
                    HaulColors.secondaryContainer,
                    CircleShape,
                ).padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun CompactHeader(
    header: HaulHeader,
    pending: Boolean,
    account: Modifier,
) {
    Strip(height = 32.dp, padding = 16.dp, size = 10f, spacing = 0.04f) { style ->
        DeliverTo(header.deliverTo, style)
        Text("HELP", style)
    }
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Logo(size = 34f, dot = 9.dp, dotMargin = 2.dp, modifier = Modifier.pressable(LocalLogoAction.current))
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.follows(header.saved).size(44.dp),
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
                Box(
                    account.size(44.dp),
                    contentAlignment = Alignment.Center,
                ) { Icon(HaulIcons.person, 24.dp, HaulColors.onSurface) }
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
    Row(
        Modifier
            .fillMaxWidth()
            // `height: 46px` with a 1 px top border outside it: 47 in all.
            .height(46.dp)
            .padding(horizontal = 16.dp)
            .clipToBounds(),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Deals(size = 14f, Modifier.follows(header.deals))
        SpacedWords(header.categories, 22.dp, HaulType.text(14f, 500), links = header.catalog)
    }
}

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

@Composable
private fun DeliverTo(
    place: String,
    style: TextStyle,
) {
    Text(
        buildAnnotatedString {
            append("DELIVER TO ")
            withStyle(
                SpanStyle(color = HaulColors.secondaryContainer, fontWeight = FontWeight(600)),
            ) { append(place.uppercase()) }
        },
        style = style,
        softWrap = false,
    )
}

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
        Box(
            Modifier
                .size(button)
                .background(HaulColors.secondaryContainer, RoundedCornerShape(buttonRadius))
                .pressable(input?.let { it::submit }),
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
    LaunchedEffect(header.query) { input.show(header.query.orEmpty()) }
    BasicTextField(
        value = input.text,
        onValueChange = input::type,
        modifier =
            modifier
                .testTag(SEARCH_FIELD_TAG)
                .onFocusChanged { field?.focused = it.isFocused }
                .onPreviewKeyEvent {
                    val enter = it.key == Key.Enter || it.key == Key.NumPadEnter
                    if (enter && it.type == KeyEventType.KeyDown) input.submit()
                    enter
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
    Row(
        modifier
            .height(height)
            .background(HaulColors.inverseSurface, RoundedCornerShape(radius))
            .padding(horizontal = horizontal),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(HaulIcons.bag, 22.dp, HaulColors.onPrimary)
        if (label) Text("Cart", HaulType.text(16f, 700).copy(color = HaulColors.onPrimary))
        // A cart with nothing in it shows no count (Home_Guest).
        if (count > 0) {
            Box(
                Modifier
                    .defaultMinSize(minWidth = 24.dp)
                    .height(24.dp)
                    .background(HaulColors.secondaryContainer, RoundedCornerShape(12.dp))
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) { Text(count.toString(), HaulType.text(13f, 800)) }
        }
    }
}

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
