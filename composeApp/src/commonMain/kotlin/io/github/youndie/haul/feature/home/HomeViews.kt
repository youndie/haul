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
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.shell.LocalScreenRefresh
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.LocalHaulCompact
import io.github.youndie.haul.theme.LocalHaulMedium
import io.github.youndie.haul.ui.CampaignHero
import io.github.youndie.haul.ui.CampaignRow
import io.github.youndie.haul.ui.CategoryGrid
import io.github.youndie.haul.ui.CategoryTile
import io.github.youndie.haul.ui.FittedText
import io.github.youndie.haul.ui.HaulButton
import io.github.youndie.haul.ui.HaulIcons
import io.github.youndie.haul.ui.Icon
import io.github.youndie.haul.ui.InlineLine
import io.github.youndie.haul.ui.LocalHaulNow
import io.github.youndie.haul.ui.PhotoOrPlaceholder
import io.github.youndie.haul.ui.PlusBlock
import io.github.youndie.haul.ui.PromoBanner
import io.github.youndie.haul.ui.RollingText
import io.github.youndie.haul.ui.SectionHeader
import io.github.youndie.haul.ui.ShrinkFirstRow
import io.github.youndie.haul.ui.Text
import io.github.youndie.haul.ui.accented
import io.github.youndie.haul.ui.countdown
import io.github.youndie.haul.ui.countdownEnded
import io.github.youndie.haul.ui.following
import io.github.youndie.haul.ui.follows
import io.github.youndie.haul.ui.gutter
import io.github.youndie.haul.ui.hatching
import io.github.youndie.haul.ui.hovers
import io.github.youndie.haul.ui.onHover
import io.github.youndie.haul.ui.rememberHover
import io.github.youndie.haul.ui.reveal
import io.github.youndie.haul.ui.toneColor
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

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
            Hero(row.hero, compact = true, Modifier.reveal().fillMaxWidth().height(440.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.banners.forEachIndexed { index, banner ->
                    Banner(banner, compact = true, Modifier.reveal(index + 1).weight(1f).height(200.dp))
                }
            }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = 28.dp).height(560.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Hero(row.hero, compact = false, Modifier.reveal().weight(2f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                row.banners.forEachIndexed { index, banner ->
                    Banner(banner, compact = false, Modifier.reveal(index + 1).fillMaxWidth().weight(1f))
                }
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
    // Hovered, the campaign's picture drifts nearer and its figure grows a little (B-158).
    val hover = rememberHover()
    Box(
        modifier
            .hovers(hover)
            // Before the clip, so that the keyboard's ring is drawn outside it (B-167).
            .follows(hero.action, shape)
            .clip(shape)
            .background(toneColor(hero.tone)),
    ) {
        // The campaign's picture (B-78) where the canvas keeps its place, under the words; the hatching while it
        // loads, and for good without one.
        PhotoOrPlaceholder(
            hero.image,
            Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .fillMaxWidth(if (compact) 0.42f else 0.4f)
                .onHover(hover) {
                    scaleX = 1f + HERO_DRIFT * it
                    scaleY = 1f + HERO_DRIFT * it
                    transformOrigin = TransformOrigin(1f, 0.5f)
                },
        ) { Box(Modifier.fillMaxSize().hatching(HaulColors.bandHatch, period = 14f)) }
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
                                ).copy(color = HaulColors.onPrimaryFixed),
                    )
                }
                // The figure is as large as the card is wide enough for (B-82): 260 px of «70%» run 50 px past
                // the card's edge at 1024.
                // A figure drawn, not words to copy: out of the page's selection, as the footer's name (B-155).
                val acid = HaulColors.secondaryContainer
                DisableSelection {
                    FittedText(
                        AnnotatedString(figure),
                        size = if (compact) 108f else 260f,
                        style = { fonts, size ->
                            HaulType
                                .display(
                                    fonts,
                                    size,
                                    900,
                                    letterSpacing = -0.05f,
                                    lineHeight = if (compact) 0.84f else 0.82f,
                                ).copy(color = acid)
                        },
                        modifier =
                            Modifier.offset(x = if (compact) (-4).dp else (-10).dp).onHover(hover) {
                                scaleX = 1f + FIGURE_GROWTH * it
                                scaleY = 1f + FIGURE_GROWTH * it
                                transformOrigin = TransformOrigin(0f, 1f)
                            },
                        softWrap = false,
                    )
                }
            }
            val subtitle =
                @Composable {
                    Text(
                        hero.subtitle,
                        HaulType.text(if (compact) 14f else 15f, lineHeight = 1.4f).copy(
                            color = HaulColors.onPrimaryFixed,
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
    // Below 1280 a banner is 304 px wide at 1024: its title is set smaller, and the placeholder's tile, which the
    // title would run into, is left out, as on a phone (B-82).
    val medium = LocalHaulMedium.current
    // Hovered, a banner rises and its picture tilts toward the pointer (B-158).
    val hover = rememberHover()
    Box(
        modifier
            .onHover(hover) { translationY = -BANNER_LIFT.toPx() * it }
            .hovers(hover)
            // Before the clip, so that the keyboard's ring is drawn outside it (B-167).
            .follows(banner.action, shape)
            .clip(shape)
            .background(toneColor(banner.tone)),
    ) {
        // The campaign's picture (B-78) in the tile the canvas keeps for it, the hatching while it loads.
        val image = banner.image?.takeUnless { medium }
        if (!compact && image != null) {
            PhotoOrPlaceholder(
                image,
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp, end = 20.dp)
                    .size(150.dp)
                    .onHover(hover) {
                        rotationZ = -BANNER_TILT * it
                        scaleX = 1f + PHOTO_GROWTH * it
                        scaleY = 1f + PHOTO_GROWTH * it
                    }.clip(RoundedCornerShape(18.dp)),
            ) { Box(Modifier.fillMaxSize().hatching(HaulColors.bannerHatch)) }
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
                    medium -> 32f
                    !compact -> 48f
                    banner.title.length > LONG_BANNER_TITLE -> 24f
                    else -> 28f
                }
            Text(
                accented(banner.title, banner.accent),
                if (!compact && image != null) Modifier.widthIn(max = 240.dp) else Modifier,
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
                        Icon(
                            HaulIcons.arrowRight,
                            if (compact) 16.dp else 18.dp,
                            HaulColors.onSurface,
                            Modifier.onHover(hover) { translationX = ARROW_LEAN.toPx() * it },
                        )
                    }
                }

                subtitle != null -> {
                    Text(
                        subtitle,
                        if (compact) {
                            HaulType.text(13f, 600, lineHeight = 1.3f)
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

/** What hover does to the home's blocks (B-158): how far things rise, lean, tilt and grow. */
private const val HERO_DRIFT = 0.07f
private const val FIGURE_GROWTH = 0.03f
private val BANNER_LIFT = 4.dp
private const val BANNER_TILT = 4f
private const val PHOTO_GROWTH = 0.05f
private val ARROW_LEAN = 5.dp
private val TILE_LIFT = 5.dp

/**
 * B-152: the deals a countdown counts end with it, and the server has the next day's from that second — a home page
 * left open over the store's midnight read 00:00:00 over ended deals until a reload. At the end the page is asked
 * for again in place ([LocalScreenRefresh]), and again every [ASK_AGAIN] while it still draws the countdown that
 * ended: a browser's clock a little ahead of the server's asks before the server's day has turned. The page that
 * arrives — the next day's countdown, or the popular row in the seconds before the deals move (TC-HOME-04) — ends
 * the asking. A view drawn on its own (a screenshot) has no refresh and asks nothing.
 */
@Composable
private fun AskAgainAtTheEnd(
    endsAt: String,
    ended: Boolean,
) {
    val refresh = LocalScreenRefresh.current
    LaunchedEffect(endsAt, ended, refresh) {
        if (!ended || refresh == null) return@LaunchedEffect
        while (true) {
            refresh.refresh()
            delay(ASK_AGAIN)
        }
    }
}

/** How long a page that still draws an ended countdown waits before it asks again. */
private val ASK_AGAIN = 30.seconds

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
    Column(
        Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = if (compact) 48.dp else 72.dp).reveal(),
    ) {
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
                        val now = LocalHaulNow.current
                        AskAgainAtTheEnd(endsAt, countdownEnded(endsAt, now))
                        // Its figures roll as they tick down (B-158).
                        RollingText(
                            countdown(endsAt, now),
                            HaulType
                                .label(
                                    if (compact) 15f else 18f,
                                    600,
                                    0.04f,
                                ).copy(color = HaulColors.inverseAccent),
                            Modifier
                                .background(HaulColors.inverseSurface, RoundedCornerShape(12.dp))
                                .padding(
                                    horizontal = if (compact) 10.dp else 14.dp,
                                    vertical = if (compact) 8.dp else 10.dp,
                                ),
                        )
                    }
                }
            }
            val link = if (compact) header.compactLinkLabel else header.linkLabel
            when {
                link != null -> {
                    val hover = rememberHover()
                    Row(
                        Modifier.hovers(hover).follows(header.action),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(link, HaulType.text(15f, 600), softWrap = false)
                        Icon(
                            HaulIcons.arrowRight,
                            18.dp,
                            HaulColors.onSurface,
                            Modifier.onHover(hover) { translationX = ARROW_LEAN.toPx() * it },
                        )
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
                row.forEachIndexed { column, tile -> Tile(tile, compact, Modifier.weight(1f).reveal(column)) }
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
    // Hovered, a tile rises and its picture comes nearer (B-158).
    val hover = rememberHover()
    Column(
        modifier.onHover(hover) { translationY = -TILE_LIFT.toPx() * it }.hovers(hover).follows(tile.action),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
    ) {
        // The category's picture (B-78) over the canvas's tile, which stays while it loads.
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(if (compact) 16.dp else 22.dp)),
        ) {
            PhotoOrPlaceholder(
                tile.image,
                Modifier.matchParentSize().onHover(hover) {
                    scaleX = 1f + PHOTO_GROWTH * 2 * it
                    scaleY = 1f + PHOTO_GROWTH * 2 * it
                },
            ) { Box(Modifier.fillMaxSize().background(toneColor(tile.tone)).hatching()) }
        }
        Text(tile.name, HaulType.text(if (compact) 12f else 16f, 600, lineHeight = 1.25f))
    }
}

/**
 * Haul Plus: the offer to a guest or a non-member, the savings to a member. The offer's button follows
 * [PlusBlock.action]: the trial's dialog for a customer, sign-in for a guest (B-23).
 */
@Composable
public fun PlusBlockView(plus: PlusBlock) {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    val box =
        Modifier
            .fillMaxWidth()
            .background(HaulColors.band, RoundedCornerShape(if (compact) 24.dp else 28.dp))
    Box(Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = if (compact) 48.dp else 72.dp).reveal()) {
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
                    .copy(color = HaulColors.onBand),
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
                plus.price?.let { Text(it, HaulType.text(15f).copy(color = HaulColors.onBandVariant)) }
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
                HaulType.label(11f, 600, 0.08f).copy(color = HaulColors.onBandVariant),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            plus.benefits.forEach {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(HaulIcons.check, 20.dp, HaulColors.secondaryContainer)
                    Text(it, HaulType.text(if (compact) 16f else 17f).copy(color = HaulColors.onBand))
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
                        onClick = following(plus.action),
                    )
                    price?.let {
                        Text(
                            it,
                            HaulType
                                .text(
                                    14f,
                                ).copy(color = HaulColors.onBandVariant, textAlign = TextAlign.Center),
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HaulButton(
                        offer,
                        height = 60.dp,
                        radius = 16.dp,
                        fill = HaulColors.secondaryContainer,
                        onClick = following(plus.action),
                    )
                    price?.let { Text(it, HaulType.text(15f).copy(color = HaulColors.onBandVariant)) }
                }
            }
        }
    }
}
