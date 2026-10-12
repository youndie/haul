package io.github.youndie.haul

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.youndie.haul.feature.search.SearchSuggestOverlay
import io.github.youndie.haul.shell.SearchError
import io.github.youndie.haul.shell.SearchLoading
import io.github.youndie.haul.shell.SearchTooShort
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.SearchSuggestPanel
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.viddik.annotations.ViddikScreenshot

// One fixture per artboard of the Search screen (B-10), at the artboard's size. Results and NoResults
// are wire bodies with the canvas's copy (`resources/bodies/search_*.json`) drawn through the app's
// registry; Autocomplete is the Results body with the suggest panel's body open over it; Loading and
// Error are the client's own, with the query the shopper typed in the header.

@ViddikScreenshot(name = "Loading", group = "Search", width = 1440, height = 1288)
@Composable
internal fun SearchLoadingWide() = Fixture(compact = false) { Page { SearchLoading("running shoes") } }

@ViddikScreenshot(name = "Loading_Phone", group = "Search", width = 390, height = 1984)
@Composable
internal fun SearchLoadingPhone() = Fixture(compact = true) { Page { SearchLoading("running shoes") } }

@ViddikScreenshot(name = "Results", group = "Search", width = 1440, height = 1359)
@Composable
internal fun SearchResultsWide() = Fixture(compact = false) { Page { Body("search_results.json") } }

@ViddikScreenshot(name = "Results_Phone", group = "Search", width = 390, height = 2196)
@Composable
internal fun SearchResultsPhone() = Fixture(compact = true) { Page { Body("search_results.json") } }

@ViddikScreenshot(name = "Autocomplete", group = "Search", width = 1440, height = 1359)
@Composable
internal fun SearchAutocompleteWide() = Fixture(compact = false) { Autocomplete() }

@ViddikScreenshot(name = "Autocomplete_Phone", group = "Search", width = 390, height = 2196)
@Composable
internal fun SearchAutocompletePhone() = Fixture(compact = true) { Autocomplete() }

@ViddikScreenshot(name = "NoResults", group = "Search", width = 1440, height = 869)
@Composable
internal fun SearchNoResultsWide() = Fixture(compact = false) { Page { Body("search_no_results.json") } }

@ViddikScreenshot(name = "NoResults_Phone", group = "Search", width = 390, height = 1010)
@Composable
internal fun SearchNoResultsPhone() = Fixture(compact = true) { Page { Body("search_no_results.json") } }

@ViddikScreenshot(name = "Error", group = "Search", width = 1440, height = 900)
@Composable
internal fun SearchErrorWide() = Fixture(compact = false) { Page { SearchError("running shoes") } }

@ViddikScreenshot(name = "Error_Phone", group = "Search", width = 390, height = 692)
@Composable
internal fun SearchErrorPhone() = Fixture(compact = true) { Page { SearchError("running shoes") } }

// No artboard: a search shorter than the server matches (B-94), drawn as the page that is not there is,
// under the shell's header with the query in its field.
@ViddikScreenshot(name = "TooShort", group = "Search", width = 1440, height = 900)
@Composable
internal fun SearchTooShortWide() = Fixture(compact = false) { Page { SearchTooShort("a") } }

@ViddikScreenshot(name = "TooShort_Phone", group = "Search", width = 390, height = 692)
@Composable
internal fun SearchTooShortPhone() = Fixture(compact = true) { Page { SearchTooShort("a") } }

// No artboard: the empty field pressed by a customer (B-124) — the panel is their recent searches alone, newest
// first, with «Clear», and no products' column; over the home page, whose field is empty.
@ViddikScreenshot(name = "Recent", group = "Search", width = 1440, height = 900)
@Composable
internal fun SearchRecentWide() = Fixture(compact = false) { Recent() }

@ViddikScreenshot(name = "Recent_Phone", group = "Search", width = 390, height = 844)
@Composable
internal fun SearchRecentPhone() = Fixture(compact = true) { Recent() }

/** The home page with the empty field pressed by Maya after `mug`, `sony`, `duvet`, `mug`. */
@Composable
private fun Recent() {
    val panel =
        remember {
            SearchSuggestPanel(
                id = "suggest",
                query = "",
                suggestions = emptyList(),
                categories = emptyList(),
                products = emptyList(),
                allResultsLabel = "",
                recent = listOf("mug", "duvet", "sony").map { Link(it, NavigateAction("/search?q=$it")) },
                clearUrl = "/api/v1/me/recent-searches",
            )
        }
    SearchSuggestOverlay(panel) { Page { Body("home_content.json") } }
}

/** The results page while the shopper types «running shoes»: the panel open, its first row highlighted. */
@Composable
private fun Autocomplete() {
    val panel = remember { decode("search_suggest.json") as SearchSuggestPanel }
    SearchSuggestOverlay(panel) { Page { Body("search_results.json") } }
}
