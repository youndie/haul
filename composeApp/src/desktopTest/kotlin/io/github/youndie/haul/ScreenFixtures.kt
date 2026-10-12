package io.github.youndie.haul

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.youndie.haul.feature.catalog.FiltersSheet
import io.github.youndie.haul.feature.home.PLUS_DIALOG_COMPACT_TOP
import io.github.youndie.haul.feature.home.PlusTrialDialogView
import io.github.youndie.haul.feature.product.DialogOverlay
import io.github.youndie.haul.shell.CatalogLoading
import io.github.youndie.haul.shell.CategoriesLoading
import io.github.youndie.haul.shell.DealsLoading
import io.github.youndie.haul.shell.ErrorShell
import io.github.youndie.haul.shell.HomeLoading
import io.github.youndie.haul.shell.ShellFailure
import io.github.youndie.haul.ui.FilteredResults
import io.github.youndie.haul.ui.PlusBlock
import io.github.youndie.haul.ui.PlusTrialDialog
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.PresentAction
import io.github.youndie.viddik.annotations.ViddikScreenshot

// One fixture per artboard of the Home and Catalog screens (B-07), at the artboard's size
// (`snapshots/design/.canvas/canvas.json`), so `viddikDesignParity` compares each with its reference while the
// artboard still describes the store; one the code has moved past is retired and reads `no ref` (research §1.6).
// Content states are wire bodies written with the canvas's copy (`resources/bodies`, research risk 3)
// drawn through the app's registry; Loading, Error and the filter sheet are the client's own and are
// drawn directly.

// Home

@ViddikScreenshot(name = "Loading", group = "Home", width = 1440, height = 2129)
@Composable
internal fun HomeLoadingWide() = Fixture(compact = false) { Page { HomeLoading() } }

@ViddikScreenshot(name = "Loading_Phone", group = "Home", width = 390, height = 2010)
@Composable
internal fun HomeLoadingPhone() = Fixture(compact = true) { Page { HomeLoading() } }

@ViddikScreenshot(name = "Content", group = "Home", width = 1440, height = 3263)
@Composable
internal fun HomeContentWide() = Fixture(compact = false) { Page { Body("home_content.json") } }

@ViddikScreenshot(name = "Content_Phone", group = "Home", width = 390, height = 3875)
@Composable
internal fun HomeContentPhone() = Fixture(compact = true) { Page { Body("home_content.json") } }

// No artboard draws a width between the two (B-82): the page at 1024, the desktop rearranged below 1280 — the
// search field on a row of its own, the category row the words that fit, the campaign's figure and the banners'
// titles smaller, four cards across. Drawn whole, footer included.
@ViddikScreenshot(name = "Content_1024", group = "Home", width = 1024, height = 4160)
@Composable
internal fun HomeContentMedium() = Fixture(compact = false, medium = true) { Page { Body("home_content.json") } }

@ViddikScreenshot(name = "Guest", group = "Home", width = 1440, height = 2627)
@Composable
internal fun HomeGuestWide() = Fixture(compact = false) { Page { Body("home_guest.json") } }

@ViddikScreenshot(name = "Guest_Phone", group = "Home", width = 390, height = 2721)
@Composable
internal fun HomeGuestPhone() = Fixture(compact = true) { Page { Body("home_guest.json") } }

@ViddikScreenshot(name = "PlusTrialDialog", group = "Home", width = 1440, height = 3134)
@Composable
internal fun HomePlusTrialDialogWide() = Fixture(compact = false) { TrialDialogOverHome() }

@ViddikScreenshot(name = "PlusTrialDialog_Phone", group = "Home", width = 390, height = 3850)
@Composable
internal fun HomePlusTrialDialogPhone() = Fixture(compact = true) { TrialDialogOverHome() }

@ViddikScreenshot(name = "Error", group = "Home", width = 1440, height = 900)
@Composable
internal fun HomeErrorWide() =
    Fixture(compact = false) {
        Page { ErrorShell("The home page", ShellFailure.Unreachable) }
    }

@ViddikScreenshot(name = "Error_Phone", group = "Home", width = 390, height = 692)
@Composable
internal fun HomeErrorPhone() =
    Fixture(compact = true) {
        Page { ErrorShell("The home page", ShellFailure.Unreachable) }
    }

// The catalog's root and the deals before their trees (B-108). No artboard draws either page: placeholders of the
// blocks each page draws, as every other page has, where they had the header alone.

@ViddikScreenshot(name = "Loading", group = "Categories", width = 1440, height = 900)
@Composable
internal fun CategoriesLoadingWide() = Fixture(compact = false) { Page { CategoriesLoading() } }

@ViddikScreenshot(name = "Loading_Phone", group = "Categories", width = 390, height = 1000)
@Composable
internal fun CategoriesLoadingPhone() = Fixture(compact = true) { Page { CategoriesLoading() } }

@ViddikScreenshot(name = "Loading", group = "Deals", width = 1440, height = 1700)
@Composable
internal fun DealsLoadingWide() = Fixture(compact = false) { Page { DealsLoading() } }

@ViddikScreenshot(name = "Loading_Phone", group = "Deals", width = 390, height = 1700)
@Composable
internal fun DealsLoadingPhone() = Fixture(compact = true) { Page { DealsLoading() } }

// Catalog

@ViddikScreenshot(name = "Loading", group = "Catalog", width = 1440, height = 1717)
@Composable
internal fun CatalogLoadingWide() = Fixture(compact = false) { Page { CatalogLoading() } }

@ViddikScreenshot(name = "Loading_Phone", group = "Catalog", width = 390, height = 2356)
@Composable
internal fun CatalogLoadingPhone() = Fixture(compact = true) { Page { CatalogLoading() } }

@ViddikScreenshot(name = "Content", group = "Catalog", width = 1440, height = 1940)
@Composable
internal fun CatalogContentWide() = Fixture(compact = false) { Page { Body("catalog_content.json") } }

@ViddikScreenshot(name = "Content_Phone", group = "Catalog", width = 390, height = 2789)
@Composable
internal fun CatalogContentPhone() = Fixture(compact = true) { Page { Body("catalog_content.json") } }

// The category at 1024 (B-82): three cards across beside the filters, the applied filters on two lines.
@ViddikScreenshot(name = "Content_1024", group = "Catalog", width = 1024, height = 2265)
@Composable
internal fun CatalogContentMedium() = Fixture(compact = false, medium = true) { Page { Body("catalog_content.json") } }

@ViddikScreenshot(name = "Empty", group = "Catalog", width = 1440, height = 1646)
@Composable
internal fun CatalogEmptyWide() = Fixture(compact = false) { Page { Body("catalog_empty.json") } }

@ViddikScreenshot(name = "Empty_Phone", group = "Catalog", width = 390, height = 852)
@Composable
internal fun CatalogEmptyPhone() = Fixture(compact = true) { Page { Body("catalog_empty.json") } }

@ViddikScreenshot(name = "Error", group = "Catalog", width = 1440, height = 900)
@Composable
internal fun CatalogErrorWide() = Fixture(compact = false) { Page { ErrorShell("This category", ShellFailure.Server) } }

@ViddikScreenshot(name = "Error_Phone", group = "Catalog", width = 390, height = 692)
@Composable
internal fun CatalogErrorPhone() = Fixture(compact = true) { Page { ErrorShell("This category", ShellFailure.Server) } }

/** The phone's filter sheet, opened over the category page whose body is Catalog_Content's. */
@ViddikScreenshot(name = "FiltersSheet_Phone", group = "Catalog", width = 390, height = 1467)
@Composable
internal fun CatalogFiltersSheetPhone() =
    Fixture(compact = true) {
        val results =
            remember {
                (
                    decode(
                        "catalog_content.json",
                    ) as ColumnComponent
                ).children.filterIsInstance<FilteredResults>().single()
            }
        FiltersSheet(results.facets, results.applied, results.showLabel)
    }

/**
 * Home_PlusTrialDialog: Sam's home page with the trial's dialog over it, the dialog the one its Plus block
 * presents (B-23) — at its phone place, 140 px down, as the storefront puts it.
 */
@Composable
private fun TrialDialogOverHome() {
    val dialog =
        remember {
            val home = decode("home_plus_trial.json") as ColumnComponent
            (
                home.children
                    .filterIsInstance<PlusBlock>()
                    .single()
                    .action as PresentAction
            ).content as PlusTrialDialog
        }
    Page { Body("home_plus_trial.json") }
    DialogOverlay(onDismiss = null, compactTop = PLUS_DIALOG_COMPACT_TOP) { PlusTrialDialogView(dialog) }
}
