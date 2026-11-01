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
import io.github.youndie.haul.shell.DemoNoteMemory
import io.github.youndie.haul.shell.DemoStripped
import io.github.youndie.haul.shell.HaulCommands
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.MemoryDemoNoteMemory
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.shell.TreeCommands
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulFonts
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.theme.rememberHaulFonts
import io.github.youndie.haul.ui.LinkCopier
import io.github.youndie.haul.ui.LocalLinkCopier
import io.github.youndie.haul.ui.LocalMotion
import io.github.youndie.haul.ui.LocalPhotoLoader
import io.github.youndie.haul.ui.Motion
import io.github.youndie.haul.ui.PhotoLoader
import io.github.youndie.kompot.realtime.KompotRealtimeSource
import kotlin.time.Clock

/**
 * The storefront's root: the theme, at the width the page has, and the page at the browser's address.
 * The canvas draws two widths, 1440 and 390; anything narrower than [COMPACT_BELOW] is drawn as the phone, and
 * the desktop narrower than [WIDE_FROM] rearranges what 1440's arrangement cannot hold (B-82).
 *
 * The entry point supplies what is the platform's: [photos] draws the product photos the trees name
 * (B-30), [transport] fetches the screens through [identity]'s headers (B-12, B-35), [history] is the
 * browser's, [clock] is the one «now» the countdowns read, [cartCommands] sends the cart's commands
 * (B-13), [commands] the requests the trees name by method and path (B-37), [checkoutCommands] the
 * checkout's (B-15) and [treeCommands] the dialogs' and «Helpful»'s (B-51), through the same headers;
 * [realtime] streams the updates of a page that names a channel, the order's (B-29); [links] copies a link
 * to a page, the product page's share button (B-71). [fonts] are the faces the entry point has already read, so the
 * first frame is drawn in them (B-150); without them the faces are loaded by Compose and swap in when they arrive.
 * [motion] is whether the page moves (B-158): [Motion.Still] where the browser asks for reduced motion.
 * [demoNote] remembers whether the visitor closed the demo strip the storefront draws above its pages (B-160): the
 * browser's `sessionStorage` there, memory by default. The strip is the root's alone: `Storefront` drawn on its own —
 * the shell's tests — and every golden have none.
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
    fonts: HaulFonts? = null,
    motion: Motion = Motion.Lively,
    demoNote: DemoNoteMemory = MemoryDemoNoteMemory(),
) {
    HaulThemeAtWidth(fonts ?: rememberHaulFonts(), Modifier.fillMaxSize().background(HaulColors.background)) {
        CompositionLocalProvider(
            LocalPhotoLoader provides photos,
            LocalLinkCopier provides links,
            LocalMotion provides motion,
        ) {
            DemoStripped(transport, demoNote) {
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

/** [HaulTheme] for the width [content] is given: the phone's below [COMPACT_BELOW], rearranged below [WIDE_FROM]. */
@Composable
public fun HaulThemeAtWidth(
    fonts: HaulFonts,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier) {
        HaulTheme(fonts, compact = maxWidth < COMPACT_BELOW, medium = maxWidth < WIDE_FROM, content = content)
    }
}

/**
 * Below 1024 px the phone's arrangement is drawn (B-82): a tablet held upright gets the phone's header with its
 * menu, one column of content and the phone's cart lines, every one of them laid out for any width from 375 up.
 * The desktop's arrangement was squeezed there before — the cart's title one letter per line at 800.
 */
private val COMPACT_BELOW = 1024.dp

/** From 1280 px the desktop is drawn as the 1440 artboards arrange it; between 1024 and 1279, rearranged. */
private val WIDE_FROM = 1280.dp
