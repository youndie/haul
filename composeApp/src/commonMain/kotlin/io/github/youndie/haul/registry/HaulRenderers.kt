package io.github.youndie.haul.registry

import androidx.compose.runtime.Composable
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.HaulHeaderView
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductCardView
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
