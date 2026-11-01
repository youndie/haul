package io.github.youndie.haul

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.youndie.haul.theme.LocalThemeChoice
import io.github.youndie.haul.theme.MemoryThemeMemory
import io.github.youndie.haul.theme.ThemeChoice
import io.github.youndie.haul.theme.ThemeMode
import io.github.youndie.haul.ui.HaulFooter
import io.github.youndie.haul.ui.HaulFooterView
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.viddik.annotations.ViddikScreenshot

// The dark scheme's goldens (B-180): the very fixtures of the light goldens, drawn in the dark scheme ([Dark]), named
// `<state>_Dark` beside them. Not every golden twice — a set that between them draws every role and every place a
// role was split for the dark: the bands (the strip, the footer, the Plus block and dialog, a message), the inverse
// fills (the cart button, chosen chips and days, the pager) and their accent, the words on Acid, the tile tones and
// the words on them, the fixed Cobalt (the campaign, the membership tile), the skeletons, a dialog's scrim and card,
// the autocomplete's scrim and shadow, the swatches, the demo strip and «Under the hood», /numbers and /live. Their
// pages have no artboard: they read `no ref` in parity, as every page the canvas never drew.

@ViddikScreenshot(name = "Content_Dark", group = "Home", width = 1440, height = 3263)
@Composable
internal fun HomeContentDark() = Dark { HomeContentWide() }

@ViddikScreenshot(name = "Content_Phone_Dark", group = "Home", width = 390, height = 3875)
@Composable
internal fun HomeContentPhoneDark() = Dark { HomeContentPhone() }

@ViddikScreenshot(name = "Loading_Dark", group = "Home", width = 1440, height = 2129)
@Composable
internal fun HomeLoadingDark() = Dark { HomeLoadingWide() }

@ViddikScreenshot(name = "PlusTrialDialog_Dark", group = "Home", width = 1440, height = 3134)
@Composable
internal fun HomePlusTrialDialogDark() = Dark { HomePlusTrialDialogWide() }

@ViddikScreenshot(name = "Content_Dark", group = "Catalog", width = 1440, height = 1940)
@Composable
internal fun CatalogContentDark() = Dark { CatalogContentWide() }

@ViddikScreenshot(name = "Autocomplete_Dark", group = "Search", width = 1440, height = 1359)
@Composable
internal fun SearchAutocompleteDark() = Dark { SearchAutocompleteWide() }

@ViddikScreenshot(name = "Description_Dark", group = "Product", width = 1440, height = 1494)
@Composable
internal fun ProductDescriptionDark() = Dark { ProductDescription() }

@ViddikScreenshot(name = "ReviewDialog_Phone_Dark", group = "Product", width = 390, height = 3067)
@Composable
internal fun ProductReviewDialogPhoneDark() = Dark { ProductReviewDialogPhone() }

@ViddikScreenshot(name = "Content_Dark", group = "Cart", width = 1440, height = 1179)
@Composable
internal fun CartContentDark() = Dark { CartContentWide() }

@ViddikScreenshot(name = "PointsApplied_Dark", group = "Checkout", width = 1440, height = 1564)
@Composable
internal fun CheckoutPointsAppliedDark() = Dark { CheckoutPointsAppliedWide() }

@ViddikScreenshot(name = "ReadyForPickup_Dark", group = "Order", width = 1440, height = 1074)
@Composable
internal fun OrderReadyForPickupDark() = Dark { OrderReadyForPickupWide() }

@ViddikScreenshot(name = "InTransit_Phone_Dark", group = "Order", width = 390, height = 1662)
@Composable
internal fun OrderInTransitPhoneDark() = Dark { OrderInTransitPhone() }

@ViddikScreenshot(name = "Content_Dark", group = "Account", width = 1440, height = 1629)
@Composable
internal fun AccountContentDark() = Dark { AccountContentWide() }

@ViddikScreenshot(name = "Message_Dark", group = "Shell", width = 1440, height = 100)
@Composable
internal fun ShellMessageDark() = Dark { ShellMessage() }

@ViddikScreenshot(name = "Strip_Hood_Dark", group = "Demo", width = 1440, height = 224)
@Composable
internal fun DemoStripHoodDark() = Dark { DemoStripHoodWide() }

@ViddikScreenshot(name = "Panel_Dark", group = "Hood", width = 1440, height = 900)
@Composable
internal fun HoodPanelDark() = Dark { HoodPanelWide() }

@ViddikScreenshot(name = "Content_Dark", group = "Numbers", width = 1440, height = 3180)
@Composable
internal fun NumbersContentDark() = Dark { NumbersContentWide() }

@ViddikScreenshot(name = "Content_Dark", group = "Live", width = 1440, height = 2464)
@Composable
internal fun LiveContentDark() = Dark { LiveContentWide() }

// The footer with the theme's switch (B-180), which only the storefront's root holds, so no page golden draws it:
// light with «System» chosen, dark with «Dark» chosen, at both widths.

@ViddikScreenshot(name = "Theme", group = "HaulFooter", width = 1440, height = 520)
@Composable
internal fun FooterThemeWide() = Fixture(compact = false) { FooterWithSwitch(ThemeMode.System) }

@ViddikScreenshot(name = "Theme_Phone", group = "HaulFooter", width = 390, height = 720)
@Composable
internal fun FooterThemePhone() = Fixture(compact = true) { FooterWithSwitch(ThemeMode.System) }

@ViddikScreenshot(name = "Theme_Dark", group = "HaulFooter", width = 1440, height = 520)
@Composable
internal fun FooterThemeDark() = Dark { Fixture(compact = false) { FooterWithSwitch(ThemeMode.Dark) } }

@ViddikScreenshot(name = "Theme_Phone_Dark", group = "HaulFooter", width = 390, height = 720)
@Composable
internal fun FooterThemePhoneDark() = Dark { Fixture(compact = true) { FooterWithSwitch(ThemeMode.Dark) } }

/** The guest home's footer (`home_guest.json`) with the switch, [chosen] chosen. */
@Composable
private fun FooterWithSwitch(chosen: ThemeMode) {
    val footer =
        remember { (decode("home_guest.json") as ColumnComponent).children.filterIsInstance<HaulFooter>().first() }
    val choice = remember(chosen) { ThemeChoice(MemoryThemeMemory(chosen)) }
    CompositionLocalProvider(LocalThemeChoice provides choice) {
        Box(Modifier.fillMaxWidth()) { HaulFooterView(footer) }
    }
}
