package io.github.youndie.haul.registry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import io.github.youndie.haul.ui.Breadcrumbs
import io.github.youndie.haul.ui.CampaignRow
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
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.ProductGridView
import io.github.youndie.haul.ui.SectionHeader
import io.github.youndie.haul.ui.gutter
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotComponentRenderer
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.registry.KompotComponentMarker

// One renderer per Haul component; kompot's processor collects them into `generatedHaulAppRenderers`.

@KompotComponentMarker
public class HaulHeaderRenderer : KompotComponentRenderer<HaulHeader> {
    @Composable
    override fun Render(
        component: HaulHeader,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        HaulHeaderView(component)
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

@KompotComponentMarker
public class HaulPaginationRenderer : KompotComponentRenderer<HaulPagination> {
    @Composable
    override fun Render(
        component: HaulPagination,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        PaginationView(component)
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
