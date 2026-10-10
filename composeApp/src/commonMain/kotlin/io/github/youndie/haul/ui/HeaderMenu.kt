package io.github.youndie.haul.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType

/**
 * The phone header's menu (B-73), held by the shell rather than by the header that draws it: like the
 * filter sheet (B-54), a page *visited* — a link, back, forward — closes it, which only the shell sees.
 * An entry pressed closes it too, before its action runs, so a `present` (the Plus trial's dialog) is not
 * drawn under it.
 */
@Stable
public class HeaderMenuState {
    public var isOpen: Boolean by mutableStateOf(false)
        private set

    /** The menu button: the menu opens over the page. */
    public fun open() {
        isOpen = true
    }

    /** «×», the scrim, an entry pressed or a page visited: the menu closes, the page under it stays. */
    public fun close() {
        isOpen = false
    }
}

/** The header menu the shell holds; none outside the storefront (a screenshot), where the button opens nothing. */
public val LocalHeaderMenu: ProvidableCompositionLocal<HeaderMenuState?> = staticCompositionLocalOf { null }

/** What the phone header's menu button is called to a screen reader, and to the tests that press it. */
public const val OPEN_MENU: String = "Menu"

/** What the menu's «×» is called. */
public const val CLOSE_MENU: String = "Close menu"

/** The tag of the open menu, for the tests that tell open from closed and find an entry inside it. */
public const val HEADER_MENU_TAG: String = "header-menu"

/**
 * The phone header's menu (B-73): what the 1440 header reaches and a 390 one has no room for — the account
 * («Sign in» for a guest), «Orders», «Saved», «Deals», «HAUL PLUS», a customer's «Sign out» (B-66), and
 * under «Catalog» every top-level category (`HaulHeader.catalog`), not only the ten of the category row.
 * No artboard draws it; it is the filter sheet's frame (Catalog_FiltersSheet_Phone) — title row, «×», a scrolling body — drawn from the
 * theme's tokens. Each entry follows what the header carries for it; [onAccount] is the account slot's
 * tap. Nothing to follow — a screenshot — draws the same rows with nothing to press.
 */
@Composable
public fun HeaderMenuSheet(
    header: HaulHeader,
    modifier: Modifier = Modifier,
    onAccount: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
) {
    // An entry closes the menu before it is followed.
    fun then(press: (() -> Unit)?): (() -> Unit)? =
        press?.let {
            {
                onClose?.invoke()
                it()
            }
        }
    Column(modifier.testTag(HEADER_MENU_TAG).fillMaxSize().background(HaulColors.surfaceContainerLowest)) {
        Row(
            Modifier.fillMaxWidth().height(68.dp).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Menu", HaulType.display(30f, 800, letterSpacing = -0.01f), softWrap = false)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(44.dp)
                    .background(HaulColors.background, CircleShape)
                    .semantics { contentDescription = CLOSE_MENU }
                    .pressable(onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(HaulIcons.close, 18.dp, HaulColors.onSurface) }
        }
        Divider()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            val guest = header.customerName == null
            MenuRow(
                HaulIcons.person,
                if (guest) "Sign in" else header.customerName.orEmpty(),
                then(onAccount),
                weight = 700,
            )
            MenuRow(HaulIcons.box, "Orders", then(following(header.orders)))
            MenuRow(HaulIcons.heart, "Saved", then(following(header.saved)))
            MenuRow(HaulIcons.bolt, "Deals", then(following(header.deals)), weight = 800, tint = HaulColors.error)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .pressable(then(following(header.plus)))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "HAUL PLUS",
                    HaulType.label(11f, 600, 0.06f),
                    Modifier
                        .background(HaulColors.secondaryContainer, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    softWrap = false,
                )
            }
            // A customer signs out here on a phone, as from the account slot's menu at 1440 (B-66).
            if (!guest) MenuRow(null, SIGN_OUT_LABEL, then(following(SIGN_OUT_ACTION)))
            Divider(Modifier.padding(vertical = 8.dp))
            Text(
                "CATALOG",
                HaulType.label(11f, 600, 0.06f).copy(color = HaulColors.outline),
                Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                softWrap = false,
            )
            header.catalog.forEach { link ->
                MenuRow(null, link.label, then(following(link.action)), weight = 500)
            }
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector?,
    label: String,
    onPress: (() -> Unit)?,
    weight: Int = 600,
    tint: Color = HaulColors.onSurface,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .pressable(onPress)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, 22.dp, tint)
        Text(label, HaulType.text(16f, weight).copy(color = tint), Modifier.weight(1f), softWrap = false)
        Icon(HaulIcons.chevronRight, 16.dp, HaulColors.outline)
    }
}

@Composable
private fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(HaulColors.outlineVariant))
}
