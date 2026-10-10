package io.github.youndie.haul

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.feature.cart.CartCommands
import io.github.youndie.haul.feature.checkout.CheckoutCommands
import io.github.youndie.haul.feature.identity.Identity
import io.github.youndie.haul.shell.BrowserHistory
import io.github.youndie.haul.shell.HaulCommands
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.shell.TreeCommands
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.theme.rememberHaulFonts
import io.github.youndie.haul.ui.LinkCopier
import io.github.youndie.haul.ui.LocalLinkCopier
import io.github.youndie.haul.ui.LocalPhotoLoader
import io.github.youndie.haul.ui.PhotoLoader
import io.github.youndie.kompot.realtime.KompotRealtimeSource
import kotlin.time.Clock

/**
 * The storefront's root: the theme, at the width the page has, and the page at the browser's address.
 * The canvas draws two widths, 1440 and 390; anything narrower than [COMPACT_BELOW] is drawn as the phone.
 *
 * The entry point supplies what is the platform's: [photos] draws the product photos the trees name
 * (B-30), [transport] fetches the screens through [identity]'s headers (B-12, B-35), [history] is the
 * browser's, [clock] is the one «now» the countdowns read, [cartCommands] sends the cart's commands
 * (B-13), [commands] the requests the trees name by method and path (B-37), [checkoutCommands] the
 * checkout's (B-15) and [treeCommands] the dialogs' and «Helpful»'s (B-51), through the same headers;
 * [realtime] streams the updates of a page that names a channel, the order's (B-29); [links] copies a link
 * to a page, the product page's share button (B-71).
 */
@Composable
public fun App(
    photos: PhotoLoader,
    transport: HaulTransport,
    history: BrowserHistory,
    identity: Identity,
    clock: Clock = Clock.System,
    cartCommands: CartCommands? = null,
    commands: HaulCommands? = null,
    checkoutCommands: CheckoutCommands? = null,
    treeCommands: TreeCommands? = null,
    realtime: KompotRealtimeSource? = null,
    links: LinkCopier? = null,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(HaulColors.background)) {
        CompositionLocalProvider(LocalPhotoLoader provides photos, LocalLinkCopier provides links) {
            HaulTheme(rememberHaulFonts(), compact = maxWidth < COMPACT_BELOW) {
                Storefront(
                    transport,
                    history,
                    identity::signIn,
                    clock,
                    cartCommands,
                    commands,
                    checkoutCommands,
                    treeCommands,
                    realtime,
                    session = identity,
                )
            }
        }
    }
}

private val COMPACT_BELOW = 768.dp
