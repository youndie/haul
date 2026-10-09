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
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.standard.NavigateAction

/**
 * The phone's filter sheet, held by the shell rather than by the page under it (B-54). Every press in
 * the sheet — a facet, «Show N more», «Clear all» — is a `navigate`, and the shell draws each address
 * as a page of its own, so a sheet the page remembered closed after every press. Held here, it stays
 * open across the navigations its own presses cause and is drawn again from each new page's results
 * ([drawn]); any other arrival — back, forward, a link outside the sheet — closes it ([arrived]).
 *
 * Between a press and the page it opens the sheet still shows the old page's facets, whose addresses
 * do not carry the press just made: a second tick then would open the page without the first. So until
 * the new page is drawn the sheet's facets follow nothing ([following]); «×» still closes it.
 */
@Stable
public class FiltersSheetState {
    /** The results the open sheet draws, and the page's handler its presses go to; `null` — closed. */
    internal var shown: Pair<FilteredResults, KompotActionHandler>? by mutableStateOf(null)
        private set

    /** Whether a press in the sheet has opened an address whose page is not drawn yet. */
    internal var following: Boolean by mutableStateOf(false)
        private set

    private var at: String? = null
    private var pressedTo: String? = null

    /** «Filters»: the sheet opens over [results], its presses going to [actions]. */
    public fun open(
        results: FilteredResults,
        actions: KompotActionHandler,
    ) {
        shown = results to actions
    }

    /** «×», «Show N items» or the scrim: the sheet closes, the page under it stays. */
    public fun close() {
        shown = null
        following = false
        pressedTo = null
    }

    /** A page drew [results]: an open sheet now shows them, and follows [actions]. */
    public fun drawn(
        results: FilteredResults,
        actions: KompotActionHandler,
    ) {
        if (shown == null) return
        shown = results to actions
        following = false
    }

    /** The shell arrived at [address]: the sheet stays only when its own press led there. */
    public fun arrived(address: String) {
        at = address
        if (address != pressedTo) close()
        pressedTo = null
    }

    internal fun pressed(action: KompotAction) {
        if (action is NavigateAction && action.deeplink != at) {
            pressedTo = action.deeplink
            following = true
        }
    }
}

/** The filter sheet the shell holds; none outside the storefront (a screenshot), where «Filters» opens nothing. */
public val LocalFiltersSheet: ProvidableCompositionLocal<FiltersSheetState?> = staticCompositionLocalOf { null }

/** The open sheet over whatever page is drawn — or on its way — under it. */
@Composable
public fun FiltersSheetOverlay(state: FiltersSheetState) {
    val (results, actions) = state.shown ?: return
    Dialog(onDismissRequest = state::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val presses =
            if (state.following) {
                null
            } else {
                KompotActionHandler { action ->
                    state.pressed(action)
                    actions.handle(action)
                }
            }
        CompositionLocalProvider(LocalHaulActions provides presses) {
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
