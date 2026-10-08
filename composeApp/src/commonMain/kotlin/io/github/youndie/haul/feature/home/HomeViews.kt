package io.github.youndie.haul.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.HaulType.browserLeading
import io.github.youndie.haul.theme.LocalHaulCompact
import io.github.youndie.haul.ui.CampaignHero
import io.github.youndie.haul.ui.CampaignRow
import io.github.youndie.haul.ui.CategoryGrid
import io.github.youndie.haul.ui.CategoryTile
import io.github.youndie.haul.ui.HaulButton
import io.github.youndie.haul.ui.HaulIcons
import io.github.youndie.haul.ui.Icon
import io.github.youndie.haul.ui.InlineLine
import io.github.youndie.haul.ui.LocalHaulNow
import io.github.youndie.haul.ui.PlusBlock
import io.github.youndie.haul.ui.PromoBanner
import io.github.youndie.haul.ui.SectionHeader
import io.github.youndie.haul.ui.ShrinkFirstRow
import io.github.youndie.haul.ui.Text
import io.github.youndie.haul.ui.accented
import io.github.youndie.haul.ui.countdown
import io.github.youndie.haul.ui.gutter
import io.github.youndie.haul.ui.hatching
import io.github.youndie.haul.ui.toneColor

// The home page's own components (screen-home), each at the width the page is drawn at; the numbers
// are the artboards' (Home_Content, Home_Guest and their _Phone twins).

/** The campaign and its two banners: side by side at 1440, stacked over a pair at 390. */
@Composable
public fun CampaignRowView(row: CampaignRow) {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    if (compact) {
        Column(
            Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Hero(row.hero, compact = true, Modifier.fillMaxWidth().height(440.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.banners.forEach { Banner(it, compact = true, Modifier.weight(1f).height(200.dp)) }
            }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = 28.dp).height(560.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Hero(row.hero, compact = false, Modifier.weight(2f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                row.banners.forEach { Banner(it, compact = false, Modifier.fillMaxWidth().weight(1f)) }
            }
        }
    }
}

@Composable
private fun Hero(
    hero: CampaignHero,
    compact: Boolean,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(if (compact) 24.dp else 28.dp)
    val lead = hero.accent?.takeIf { hero.title.startsWith(it) }
    val figure = if (lead != null) hero.title.removePrefix(lead).trim() else hero.title
    Box(modifier.clip(shape).background(toneColor(hero.tone))) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .fillMaxWidth(if (compact) 0.42f else 0.4f)
                .hatching(HERO_HATCH, period = 14f),
        ) {
            if (!compact) {
                Text(
                    hero.label.uppercase(),
                    HaulType.label(11f, 500, 0.06f).copy(color = HERO_LABEL),
                    Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 20.dp),
                )
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = if (compact) 24.dp else 48.dp, vertical = if (compact) 28.dp else 44.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            InlineLine(
                hero.eyebrow.uppercase(),
                HaulType.label(if (compact) 12f else 13f, 600, 0.08f).copy(color = HaulColors.secondaryContainer),
                strut = HaulType.text(16f),
            )
            Column {
                if (lead != null) {
                    Text(
                        accented(lead, lead),
                        style =
                            HaulType
                                .display(
                                    if (compact) 40f else 72f,
                                    500,
                                    letterSpacing = if (compact) -0.01f else -0.02f,
                                ).copy(color = HaulColors.onPrimary),
                    )
                }
                Text(
                    figure,
                    HaulType
                        .display(
                            if (compact) 108f else 260f,
                            900,
                            letterSpacing = -0.05f,
                            lineHeight = if (compact) 0.84f else 0.82f,
                        ).copy(color = HaulColors.secondaryContainer),
                    Modifier.offset(x = if (compact) (-4).dp else (-10).dp),
                    softWrap = false,
                )
            }
            val subtitle =
                @Composable {
                    Text(
                        hero.subtitle,
                        HaulType.text(if (compact) 14f else 15f, lineHeight = 1.4f).browserLeading().copy(
                            color = HaulColors.onPrimary,
                        ),
                        Modifier.widthIn(max = if (compact) 260.dp else 230.dp),
                    )
                }
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    subtitle()
                    HaulButton(
                        hero.actionLabel,
                        height = 52.dp,
                        radius = 14.dp,
                        horizontal = 22.dp,
                        fill = HaulColors.secondaryContainer,
                        textSize = 16f,
                        icon = HaulIcons.arrowRight,
                        iconSize = 18.dp,
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HaulButton(
                        hero.actionLabel,
                        height = 60.dp,
                        radius = 16.dp,
                        fill = HaulColors.secondaryContainer,
                        icon = HaulIcons.arrowRight,
                    )
                    subtitle()
                }
            }
        }
    }
}

@Composable
private fun Banner(
    banner: PromoBanner,
    compact: Boolean,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(if (compact) 24.dp else 28.dp)
    Box(modifier.clip(shape).background(toneColor(banner.tone))) {
        val label = banner.label
        if (!compact && label != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp, end = 20.dp)
                    .size(150.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .hatching(BANNER_HATCH),
            ) {
                Text(
                    label.uppercase(),
                    HaulType.label(10f, 500, 0f).copy(color = BANNER_LABEL),
                    Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 12.dp),
                )
            }
        }
        Column(
            Modifier.fillMaxSize().padding(if (compact) 20.dp else 32.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(banner.eyebrow.uppercase(), HaulType.label(if (compact) 11f else 12f, 600, 0.08f))
            // The phone's banner shrinks a long title (Home_Guest_Phone: 28 px for «Laptops from $399»,
            // 24 px for «Free delivery on everything»).
            val size =
                when {
                    !compact -> 48f
                    banner.title.length > LONG_BANNER_TITLE -> 24f
                    else -> 28f
                }
            Text(
                accented(banner.title, banner.accent),
                if (!compact && label != null) Modifier.widthIn(max = 240.dp) else Modifier,
                style =
                    HaulType.display(
                        size,
                        800,
                        letterSpacing = if (compact) -0.01f else -0.02f,
                        lineHeight = 0.95f,
                    ),
            )
            val action = banner.actionLabel
            val subtitle = banner.subtitle
            when {
                action != null -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(action, HaulType.text(if (compact) 14f else 15f, 700))
                        Icon(HaulIcons.arrowRight, if (compact) 16.dp else 18.dp, HaulColors.onSurface)
                    }
                }

                subtitle != null -> {
                    Text(
                        subtitle,
                        if (compact) {
                            HaulType.text(13f, 600, lineHeight = 1.3f).browserLeading()
                        } else {
                            HaulType.text(15f, 600)
                        },
                    )
                }
            }
        }
    }
}

private const val LONG_BANNER_TITLE = 20

/** White at 9 %: the campaign's hatching over Cobalt. */
private val HERO_HATCH = Color(0x17FFFFFF)

/** White at 65 %: the campaign's placeholder label. */
private val HERO_LABEL = Color(0xA6FFFFFF)

/** Ink at 8 % and 55 %: the banner's placeholder tile. */
private val BANNER_HATCH = Color(0x140F0F0F)
private val BANNER_LABEL = Color(0x8C0F0F0F)

/**
 * A section's heading: the title with its accent, the deals countdown read against [LocalHaulNow],
 * and the link (or, for «Picked for you», the subtitle) at the right; a phone puts the subtitle under.
 */
@Composable
public fun SectionHeaderView(header: SectionHeader) {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    // On a phone the subtitle sits under the title row: `margin: -8px 0 18px` after the row's 18.
    val subtitleUnder = compact && header.subtitle != null && header.linkLabel == null
    Column(Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = if (compact) 48.dp else 72.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(
                bottom =
                    when {
                        subtitleUnder -> 10.dp
                        compact -> 18.dp
                        else -> 28.dp
                    },
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = if (compact) Alignment.CenterVertically else Alignment.Bottom,
        ) {
            Box(Modifier.weight(1f)) {
                ShrinkFirstRow(gap = if (compact) 12.dp else 20.dp) {
                    Text(
                        accented(header.title, header.accent),
                        style = HaulType.display(if (compact) 34f else 56f, 800, letterSpacing = -0.01f),
                    )
                    header.countdownEndsAt?.let { endsAt ->
                        Text(
                            countdown(endsAt, LocalHaulNow.current),
                            HaulType
                                .label(
                                    if (compact) 15f else 18f,
                                    600,
                                    0.04f,
                                ).copy(color = HaulColors.secondaryContainer),
                            Modifier
                                .background(HaulColors.inverseSurface, RoundedCornerShape(12.dp))
                                .padding(
                                    horizontal = if (compact) 10.dp else 14.dp,
                                    vertical = if (compact) 8.dp else 10.dp,
                                ),
                            softWrap = false,
                        )
                    }
                }
            }
            val link = if (compact) header.compactLinkLabel else header.linkLabel
            when {
                link != null -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(link, HaulType.text(15f, 600), softWrap = false)
                        Icon(HaulIcons.arrowRight, 18.dp, HaulColors.onSurface)
                    }
                }

                header.subtitle != null && !compact -> {
                    Text(header.subtitle.orEmpty(), HaulType.text(15f, 600), softWrap = false)
                }
            }
        }
        if (subtitleUnder) {
            Text(
                header.subtitle.orEmpty(),
                HaulType.text(14f, 600).copy(color = HaulColors.outline),
                Modifier.padding(bottom = 18.dp),
            )
        }
    }
}

/** The category tiles: eight across at 1440, four across at 390. */
@Composable
public fun CategoryGridView(grid: CategoryGrid) {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    val columns = if (compact) 4 else 8
    Column(
        Modifier.fillMaxWidth().padding(horizontal = gutter),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        grid.tiles.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)) {
                row.forEach { Tile(it, compact, Modifier.weight(1f)) }
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Tile(
    tile: CategoryTile,
    compact: Boolean,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(if (compact) 16.dp else 22.dp))
                .background(toneColor(tile.tone))
                .hatching(),
        ) {
            if (!compact) {
                Text(
                    tile.label.uppercase(),
                    HaulType.label(10f, 500, 0.06f).copy(color = HaulColors.tileLabel),
                    Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 12.dp),
                )
            }
        }
        Text(tile.name, HaulType.text(if (compact) 12f else 16f, 600, lineHeight = 1.25f).browserLeading())
    }
}

/** Haul Plus: the offer to a guest or a non-member, the savings to a member. */
@Composable
public fun PlusBlockView(plus: PlusBlock) {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    val box =
        Modifier
            .fillMaxWidth()
            .background(HaulColors.inverseSurface, RoundedCornerShape(if (compact) 24.dp else 28.dp))
    Box(Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = if (compact) 48.dp else 72.dp)) {
        if (compact) {
            Column(
                box.padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                PlusTitle(plus, compact = true)
                PlusDetails(plus, compact = true)
            }
        } else {
            Row(
                box.padding(56.dp),
                horizontalArrangement = Arrangement.spacedBy(56.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(Modifier.weight(1.3f)) { PlusTitle(plus, compact = false) }
                Box(Modifier.weight(1f)) { PlusDetails(plus, compact = false) }
            }
        }
    }
}

@Composable
private fun PlusTitle(
    plus: PlusBlock,
    compact: Boolean,
) {
    Column {
        InlineLine(
            (if (plus.member) "Haul Plus · member" else "Haul Plus").uppercase(),
            HaulType.label(12f, 600, 0.08f).copy(color = HaulColors.secondaryContainer),
            strut = HaulType.text(16f),
            modifier = Modifier.padding(bottom = if (compact) 18.dp else 24.dp),
        )
        val size =
            when {
                !compact -> 84f
                plus.member -> 44f
                else -> 48f
            }
        Text(
            accented(plus.title, plus.accent, HaulColors.secondaryContainer),
            style =
                HaulType
                    .display(size, 800, letterSpacing = if (compact) -0.01f else -0.03f, lineHeight = 0.92f)
                    .copy(color = HaulColors.inverseOnSurface),
        )
        if (plus.member) {
            Row(
                Modifier.padding(top = if (compact) 16.dp else 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                plus.renewal?.let {
                    Text(
                        it.uppercase(),
                        HaulType.label(12f, 600, 0.06f),
                        Modifier
                            .background(HaulColors.secondaryContainer, CircleShape)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                plus.price?.let { Text(it, HaulType.text(15f).copy(color = HaulColors.inverseOnSurfaceVariant)) }
            }
        }
    }
}

@Composable
private fun PlusDetails(
    plus: PlusBlock,
    compact: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (plus.member) 16.dp else 24.dp)) {
        if (plus.member) {
            Text(
                "Your benefits".uppercase(),
                HaulType.label(11f, 600, 0.08f).copy(color = HaulColors.inverseOnSurfaceVariant),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            plus.benefits.forEach {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(HaulIcons.check, 20.dp, HaulColors.secondaryContainer)
                    Text(it, HaulType.text(if (compact) 16f else 17f).copy(color = HaulColors.inverseOnSurface))
                }
            }
        }
        val offer = plus.offer
        if (!plus.member && offer != null) {
            val price = plus.price
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HaulButton(
                        offer,
                        Modifier.fillMaxWidth(),
                        height = 56.dp,
                        radius = 18.dp,
                        fill = HaulColors.secondaryContainer,
                    )
                    price?.let {
                        Text(
                            it,
                            HaulType
                                .text(
                                    14f,
                                ).copy(color = HaulColors.inverseOnSurfaceVariant, textAlign = TextAlign.Center),
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HaulButton(offer, height = 60.dp, radius = 16.dp, fill = HaulColors.secondaryContainer)
                    price?.let { Text(it, HaulType.text(15f).copy(color = HaulColors.inverseOnSurfaceVariant)) }
                }
            }
        }
    }
}
