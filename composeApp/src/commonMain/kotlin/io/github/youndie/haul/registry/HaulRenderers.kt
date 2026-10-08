package io.github.youndie.haul.registry

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.youndie.haul.feature.cart.CartBodyView
import io.github.youndie.haul.feature.cart.LocalCartCommands
import io.github.youndie.haul.feature.cart.run
import io.github.youndie.haul.feature.catalog.BreadcrumbsView
import io.github.youndie.haul.feature.catalog.FilterChipsView
import io.github.youndie.haul.feature.catalog.FilteredResultsView
import io.github.youndie.haul.feature.catalog.FiltersSheet
import io.github.youndie.haul.feature.catalog.PageTitleView
import io.github.youndie.haul.feature.catalog.PaginationView
import io.github.youndie.haul.feature.home.CampaignRowView
import io.github.youndie.haul.feature.home.CategoryGridView
import io.github.youndie.haul.feature.home.PlusBlockView
import io.github.youndie.haul.feature.home.SectionHeaderView
import io.github.youndie.haul.feature.product.ProductDescriptionView
import io.github.youndie.haul.feature.product.ProductDetailsView
import io.github.youndie.haul.feature.product.ProductQuestionsView
import io.github.youndie.haul.feature.product.ProductReviewsView
import io.github.youndie.haul.feature.product.ProductTabsView
import io.github.youndie.haul.feature.product.SpecificationListView
import io.github.youndie.haul.feature.search.SearchNoResultsView
import io.github.youndie.haul.theme.LocalHaulCompact
import io.github.youndie.haul.ui.Breadcrumbs
import io.github.youndie.haul.ui.CampaignRow
import io.github.youndie.haul.ui.CartBody
import io.github.youndie.haul.ui.CategoryGrid
import io.github.youndie.haul.ui.EmptyState
import io.github.youndie.haul.ui.EmptyStateView
import io.github.youndie.haul.ui.FilterChips
import io.github.youndie.haul.ui.FilteredResults
import io.github.youndie.haul.ui.HaulFooter
import io.github.youndie.haul.ui.HaulFooterView
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.HaulHeaderView
import io.github.youndie.haul.ui.HaulPagination
import io.github.youndie.haul.ui.PageTitle
import io.github.youndie.haul.ui.PlusBlock
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductCardView
import io.github.youndie.haul.ui.ProductDescription
import io.github.youndie.haul.ui.ProductDetails
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.ProductGridView
import io.github.youndie.haul.ui.ProductQuestions
import io.github.youndie.haul.ui.ProductReviews
import io.github.youndie.haul.ui.ProductTabs
import io.github.youndie.haul.ui.SearchNoResults
import io.github.youndie.haul.ui.SectionHeader
import io.github.youndie.haul.ui.SpecificationList
import io.github.youndie.haul.ui.gutter
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotComponentRenderer
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.registry.KompotComponentMarker
import kotlinx.coroutines.launch

// One renderer per Haul component; kompot's processor collects them into `generatedHaulAppRenderers`.

@KompotComponentMarker
public class HaulHeaderRenderer : KompotComponentRenderer<HaulHeader> {
    @Composable
    override fun Render(
        component: HaulHeader,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        HaulHeaderView(component, onAccount = component.account?.let { action -> { actionHandler.handle(action) } })
    }
}

@KompotComponentMarker
public class ProductCardRenderer : KompotComponentRenderer<ProductCard> {
    @Composable
    override fun Render(
        component: ProductCard,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        ProductCardView(component)
    }
}

// The home page (screen-home).

@KompotComponentMarker
public class CampaignRowRenderer : KompotComponentRenderer<CampaignRow> {
    @Composable
    override fun Render(
        component: CampaignRow,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        CampaignRowView(component)
    }
}

@KompotComponentMarker
public class SectionHeaderRenderer : KompotComponentRenderer<SectionHeader> {
    @Composable
    override fun Render(
        component: SectionHeader,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        SectionHeaderView(component)
    }
}

@KompotComponentMarker
public class CategoryGridRenderer : KompotComponentRenderer<CategoryGrid> {
    @Composable
    override fun Render(
        component: CategoryGrid,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        CategoryGridView(component)
    }
}

/** A grid standing on the page itself, so it takes the page's gutter. */
@KompotComponentMarker
public class ProductGridRenderer : KompotComponentRenderer<ProductGrid> {
    @Composable
    override fun Render(
        component: ProductGrid,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        ProductGridView(component, gutter = gutter())
    }
}

@KompotComponentMarker
public class PlusBlockRenderer : KompotComponentRenderer<PlusBlock> {
    @Composable
    override fun Render(
        component: PlusBlock,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        PlusBlockView(component)
    }
}

@KompotComponentMarker
public class HaulFooterRenderer : KompotComponentRenderer<HaulFooter> {
    @Composable
    override fun Render(
        component: HaulFooter,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        HaulFooterView(component)
    }
}

// The category page (screen-catalog).

@KompotComponentMarker
public class BreadcrumbsRenderer : KompotComponentRenderer<Breadcrumbs> {
    @Composable
    override fun Render(
        component: Breadcrumbs,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        BreadcrumbsView(component)
    }
}

@KompotComponentMarker
public class PageTitleRenderer : KompotComponentRenderer<PageTitle> {
    @Composable
    override fun Render(
        component: PageTitle,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        PageTitleView(component)
    }
}

@KompotComponentMarker
public class FilterChipsRenderer : KompotComponentRenderer<FilterChips> {
    @Composable
    override fun Render(
        component: FilterChips,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        FilterChipsView(component)
    }
}

/** The results with their filters; on a phone «Filters» opens the facets as a full-screen sheet. */
@KompotComponentMarker
public class FilteredResultsRenderer : KompotComponentRenderer<FilteredResults> {
    @Composable
    override fun Render(
        component: FilteredResults,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        var sheet by remember { mutableStateOf(false) }
        FilteredResultsView(component, onOpenFilters = { sheet = true })
        if (sheet) {
            Dialog(
                onDismissRequest = { sheet = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                FiltersSheet(component.facets, component.applied, component.showLabel, onClose = { sheet = false })
            }
        }
    }
}

/**
 * Pages standing on the page itself, under a search's grid: the page's gutter, and the gap the
 * category page leaves above its own (which draws them inside `FilteredResults`).
 */
@KompotComponentMarker
public class HaulPaginationRenderer : KompotComponentRenderer<HaulPagination> {
    @Composable
    override fun Render(
        component: HaulPagination,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        val compact = LocalHaulCompact.current
        PaginationView(
            component,
            Modifier.padding(start = gutter(), end = gutter(), top = if (compact) 36.dp else 48.dp),
        )
    }
}

@KompotComponentMarker
public class EmptyStateRenderer : KompotComponentRenderer<EmptyState> {
    @Composable
    override fun Render(
        component: EmptyState,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        EmptyStateView(component)
    }
}

// The product page (screen-product).

@KompotComponentMarker
public class ProductDetailsRenderer : KompotComponentRenderer<ProductDetails> {
    @Composable
    override fun Render(
        component: ProductDetails,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        ProductDetailsView(component)
    }
}

@KompotComponentMarker
public class ProductTabsRenderer : KompotComponentRenderer<ProductTabs> {
    @Composable
    override fun Render(
        component: ProductTabs,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        ProductTabsView(component)
    }
}

@KompotComponentMarker
public class ProductDescriptionRenderer : KompotComponentRenderer<ProductDescription> {
    @Composable
    override fun Render(
        component: ProductDescription,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        ProductDescriptionView(component)
    }
}

@KompotComponentMarker
public class SpecificationListRenderer : KompotComponentRenderer<SpecificationList> {
    @Composable
    override fun Render(
        component: SpecificationList,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        SpecificationListView(component)
    }
}

@KompotComponentMarker
public class ProductReviewsRenderer : KompotComponentRenderer<ProductReviews> {
    @Composable
    override fun Render(
        component: ProductReviews,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        ProductReviewsView(component)
    }
}

@KompotComponentMarker
public class ProductQuestionsRenderer : KompotComponentRenderer<ProductQuestions> {
    @Composable
    override fun Render(
        component: ProductQuestions,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        ProductQuestionsView(component)
    }
}

// The search page (screen-search). The suggest panel is not a page's component: the screen draws it
// over the page while the shopper types (`SearchSuggestOverlay`).

@KompotComponentMarker
public class SearchNoResultsRenderer : KompotComponentRenderer<SearchNoResults> {
    @Composable
    override fun Render(
        component: SearchNoResults,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        SearchNoResultsView(component)
    }
}

// The cart (screen-cart). A press is a command to the server (`LocalCartCommands`, the storefront's),
// and the server's answer — `refresh` — goes to the screen's handler, which draws the cart again.

@KompotComponentMarker
public class CartBodyRenderer : KompotComponentRenderer<CartBody> {
    @Composable
    override fun Render(
        component: CartBody,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        val commands = LocalCartCommands.current
        val scope = rememberCoroutineScope()
        CartBodyView(component) { batch ->
            if (commands != null) scope.launch { commands.run(batch)?.let(actionHandler::handle) }
        }
    }
}
