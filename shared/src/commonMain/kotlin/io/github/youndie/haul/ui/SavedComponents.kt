package io.github.youndie.haul.ui

import kotlinx.serialization.Serializable

// The Saved list (feature-account, screen-saved, B-20): the account's body on `/account/saved`, its title
// «Saved» with the count of everything saved. Under it either the filter, the cards and the pages, or —
// nothing saved yet — the empty state with how the heart works. The cards are product cards as everywhere,
// each with its heart filled and, when the product got cheaper since it was saved, the «Price dropped»
// mark (`ProductCard.drop`).

/**
 * The list under the title: the [filters] «All» and «Price dropped», each with its count and its address;
 * the [cards] of the page shown, newest first; the [pagination] when there is more than one page. [empty]
 * and its [steps] are drawn instead when nothing is saved (`Saved_Empty`), and [none] when the filter
 * matches nothing — «Price dropped» with no product cheaper than when it was saved.
 */
@Serializable
public data class SavedList(
    val filters: List<HistoryFilter> = emptyList(),
    val cards: List<ProductCard> = emptyList(),
    val pagination: HaulPagination? = null,
    val empty: EmptyState? = null,
    val steps: List<SavedStep> = emptyList(),
    val none: String? = null,
)

/** One card of how the Saved list works, under the empty state: its [number] in mono, the [title], the [text]. */
@Serializable
public data class SavedStep(
    val number: String,
    val title: String,
    val text: String,
)
