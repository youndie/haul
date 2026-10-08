package io.github.youndie.haul.ui

import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.registry.KompotComponentMarker
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The pieces of the Search screen (feature-search). The results page is the catalog's own pieces —
// `PageTitle` (quoted) with the count, `FilterChips` whose chips carry counts, `ProductGrid`, `HaulPagination` —
// and these are what search adds: the suggest panel and the page for a query that found nothing.

/**
 * A query the panel suggests. [typed] is the part the shopper typed, drawn regular, and [prefix] and
 * [completion] what the suggestion adds before and after it, drawn bold («running sh» + «oes»,
 * «trail » + «running shoes»); a suggestion that does not contain what was typed (a corrected
 * spelling) has an empty [typed] and is all [completion].
 */
@Serializable
public data class QuerySuggestion(
    val typed: String,
    val completion: String,
    val action: @Polymorphic KompotAction? = null,
    val prefix: String = "",
)

/** A category the query's products fall in: «Sports › Running shoes» and how many of them. */
@Serializable
public data class CategorySuggestion(
    val label: String,
    val count: String,
    val action: @Polymorphic KompotAction? = null,
)

/** A product the panel shows: its tile, title and price. */
@Serializable
public data class ProductSuggestion(
    val productId: String,
    val title: String,
    val price: String,
    val tone: String,
    val label: String,
    val action: @Polymorphic KompotAction? = null,
)

/**
 * The panel under the search field while the shopper types (`GET /ui/search/suggest`): up to five
 * queries, three categories with counts, the shopper's recent searches (a guest has none), three top
 * products, and the way to every result («All 14,870 results ↵»). [clearAction] empties [recent].
 */
@Serializable
@SerialName("haul_search_suggest_panel")
@KompotComponentMarker
public data class SearchSuggestPanel(
    override val id: String,
    val query: String,
    val suggestions: List<QuerySuggestion>,
    val categories: List<CategorySuggestion>,
    val products: List<ProductSuggestion>,
    val allResultsLabel: String,
    val recent: List<String> = emptyList(),
    val allResultsAction: @Polymorphic KompotAction? = null,
    val clearAction: @Polymorphic KompotAction? = null,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/**
 * A search that found nothing (`Search_NoResults`): [count] above the title naming the query, whose
 * [accent] — the quoted query — is drawn in the italic; then the queries to try instead when there
 * are any, and otherwise [tips]. The popular categories follow it as a `SectionHeader` and a
 * `CategoryGrid` of their own.
 */
@Serializable
@SerialName("haul_search_no_results")
@KompotComponentMarker
public data class SearchNoResults(
    override val id: String,
    val query: String,
    val title: String,
    val suggestions: List<QuerySuggestion> = emptyList(),
    val tips: List<String> = emptyList(),
    val count: String? = null,
    val accent: String? = null,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent
