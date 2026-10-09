package io.github.youndie.haul.feature.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.youndie.haul.ui.FilteredResults
import io.github.youndie.haul.ui.LocalHaulActions
import io.github.youndie.kompot.KompotActionHandler

/**
 * The phone's filter sheet, held by the shell rather than by the page under it (B-54).
 *
 * Since B-63 every press in the sheet — a facet, «Show N more», a chip, «Clear all» — is a `load`, answered
 * with an `update` of the results the sheet is drawn from: the page is not left, so the sheet stays open by
 * itself, and the results renderer hands it the updated results ([drawn]). What is left here is what only
 * the shell sees: a page *visited* — a link, back, forward, an answer that could not be partial — closes it
 * ([close], called by the shell), where an address an `update` recorded does not. A sheet held by the
 * renderer would stay open on back, which keeps the page within its path (B-62).
 *
 * Between a press and its answer the sheet still shows the facets before the press, whose addresses lack
 * it: a second tick then would load the page without the first, and its answer — the last press wins —
 * would drop the first. So while the screen's `load` is on its way the sheet's facets follow nothing (the
 * overlay's `loading`); «×» still closes it. An answer that does not arrive ends the load all the same.
 */
@Stable
public class FiltersSheetState {
    /** The results the open sheet draws, and the page's handler its presses go to; `null` — closed. */
    internal var shown: Pair<FilteredResults, KompotActionHandler>? by mutableStateOf(null)
        private set

    /** «Filters»: the sheet opens over [results], its presses going to [actions]. */
    public fun open(
        results: FilteredResults,
        actions: KompotActionHandler,
    ) {
        shown = results to actions
    }

    /** «×», «Show N items», the scrim or a page visited: the sheet closes, the page under it stays. */
    public fun close() {
        shown = null
    }

    /** The page drew [results] — its own or an update's: an open sheet now shows them, and follows [actions]. */
    public fun drawn(
        results: FilteredResults,
        actions: KompotActionHandler,
    ) {
        if (shown == null) return
        shown = results to actions
    }
}

/** The filter sheet the shell holds; none outside the storefront (a screenshot), where «Filters» opens nothing. */
public val LocalFiltersSheet: ProvidableCompositionLocal<FiltersSheetState?> = staticCompositionLocalOf { null }

/** The open sheet over the page under it; while [loading] its presses do nothing. */
@Composable
public fun FiltersSheetOverlay(
    state: FiltersSheetState,
    loading: Boolean,
) {
    val (results, actions) = state.shown ?: return
    Dialog(onDismissRequest = state::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalHaulActions provides actions.takeUnless { loading }) {
            FiltersSheet(
                results.facets,
                results.applied,
                results.showLabel,
                onClose = state::close,
                onShow = state::close,
            )
        }
    }
}
