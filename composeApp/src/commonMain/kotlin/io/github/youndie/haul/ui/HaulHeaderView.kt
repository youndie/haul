package io.github.youndie.haul.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.LocalHaulCompact

/**
 * The header, at the width the page is drawn at (`HaulHeader` on the wire). [pending] is the client's
 * own header before any tree has arrived (Loading, Error): who is looking is not known yet, so the
 * account slot is a placeholder and the cart has no count.
 */
@Composable
public fun HaulHeaderView(
    header: HaulHeader,
    modifier: Modifier = Modifier,
    pending: Boolean = false,
) {
    val compact = LocalHaulCompact.current
    Column(
        modifier
            .fillMaxWidth()
            .background(HaulColors.surfaceContainerLowest),
    ) {
        if (compact) CompactHeader(header, pending) else WideHeader(header, pending)
        Box(Modifier.fillMaxWidth().height(1.dp).background(HaulColors.outlineVariant))
    }
}

@Composable
private fun WideHeader(
    header: HaulHeader,
    pending: Boolean,
) {
    Strip(height = 36.dp, padding = 48.dp, size = 11f, spacing = 0.06f) { style ->
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            DeliverTo(header.deliverTo, style)
            Text(header.deliveryPromise.uppercase(), style)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            listOf("Sell on HAUL", "Help", "EN · USD").forEach { Text(it.uppercase(), style) }
        }
    }
    Row(
        Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Logo(size = 44f, dot = 11.dp, dotMargin = 3.dp, modifier = Modifier.width(132.dp))
        Row(
            Modifier
                .height(56.dp)
                .background(HaulColors.primary, RoundedCornerShape(16.dp))
                .padding(start = 18.dp, end = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(HaulIcons.grid, 20.dp, HaulColors.onPrimary)
            Text("Catalog", HaulType.text(16f, 700).copy(color = HaulColors.onPrimary))
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
            Shortcut(HaulIcons.box, "Orders")
            Shortcut(HaulIcons.heart, "Saved")
            when {
                pending -> Shortcut(HaulIcons.person) { Skeleton(Modifier.width(40.dp).height(10.dp), 5.dp) }
                header.customerName == null -> Shortcut(HaulIcons.person, "Sign in", weight = 700)
                else -> Shortcut(HaulIcons.person, header.customerName.orEmpty())
            }
            CartButton(
                if (pending) 0 else header.cartCount,
                Modifier.padding(start = 6.dp),
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
        Deals(size = 15f)
        header.categories.forEach { Text(it, HaulType.text(15f, 500)) }
        Spacer(Modifier.weight(1f))
        Text(
            "HAUL PLUS",
            HaulType.label(11f, 600, 0.06f),
            Modifier
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
        Logo(size = 34f, dot = 9.dp, dotMargin = 2.dp)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(44.dp),
            contentAlignment = Alignment.Center,
        ) { Icon(HaulIcons.heart, 24.dp, HaulColors.onSurface) }
        when {
            pending -> {
                Skeleton(Modifier.width(44.dp).height(20.dp), 6.dp)
            }

            header.customerName == null -> {
                Box(Modifier.height(44.dp).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                    Text("Sign in", HaulType.text(15f, 700), softWrap = false)
                }
            }

            else -> {
                Box(
                    Modifier.size(44.dp),
                    contentAlignment = Alignment.Center,
                ) { Icon(HaulIcons.person, 24.dp, HaulColors.onSurface) }
            }
        }
        CartButton(
            if (pending) 0 else header.cartCount,
            Modifier.padding(start = 4.dp),
            height = 44.dp,
            radius = 14.dp,
            horizontal = 12.dp,
            gap = 8.dp,
            label = false,
        )
    }
    Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
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
            .height(45.dp)
            .padding(horizontal = 16.dp)
            .clipToBounds(),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Deals(size = 14f)
        header.categories.forEach { Text(it, HaulType.text(14f, 500), softWrap = false) }
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
private fun Logo(
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
    Row(
        modifier
            .height(height)
            .background(HaulColors.surfaceContainerLowest, shape)
            .border(2.dp, HaulColors.onSurface, shape)
            .padding(start = start, end = end),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            val query = header.query
            if (query == null) {
                Text(
                    header.searchPlaceholder,
                    HaulType.text(textSize).copy(color = HaulColors.outlineMuted),
                    softWrap = false,
                )
            } else {
                Text(query, HaulType.text(textSize, 500), softWrap = false)
                Box(
                    Modifier
                        .padding(start = 2.dp)
                        .width(2.dp)
                        .height(20.dp)
                        .background(HaulColors.primary),
                )
            }
        }
        trailing()
        Box(
            Modifier.size(button).background(HaulColors.secondaryContainer, RoundedCornerShape(buttonRadius)),
            contentAlignment = Alignment.Center,
        ) { Icon(HaulIcons.search, glyph, HaulColors.onSurface) }
    }
}

@Composable
private fun Shortcut(
    icon: ImageVector,
    label: String,
    weight: Int = 500,
) {
    Shortcut(icon) { Text(label, HaulType.text(12f, weight)) }
}

@Composable
private fun Shortcut(
    icon: ImageVector,
    label: @Composable () -> Unit,
) {
    Column(
        Modifier.width(76.dp),
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
private fun Deals(size: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
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
