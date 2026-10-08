package io.github.youndie.haul.registry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.github.youndie.haul.ui.LocalHaulActions
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotComponentRenderer
import io.github.youndie.kompot.KompotRegistry
import io.github.youndie.kompot.RenderersMap
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.generated.generatedHaulAppRenderers
import io.github.youndie.kompot.generated.generatedHaulContractSerializersModule
import io.github.youndie.kompot.kompotCoreRenderers
import io.github.youndie.kompot.kompotJson
import io.github.youndie.kompot.kompotStandardRenderers
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/**
 * What a body from the server is decoded with: kompot's own types plus every Haul component, read the
 * way the server writes them (`haulWireJson`): a null field is left out, so an absent nullable field is
 * null even where the contract gives it no default.
 */
@OptIn(ExperimentalSerializationApi::class)
public val haulJson: Json = Json(kompotJson(generatedHaulContractSerializersModule)) { explicitNulls = false }

/**
 * Every renderer the storefront has: kompot's core and standard sets (the page is a `column`) and one
 * per Haul component. Each Haul renderer hands the views under it the action handler kompot gave it
 * ([LocalHaulActions]), which is how a card deep in a grid follows its own action.
 */
public fun haulRegistry(): KompotRegistry =
    KompotRegistry(kompotCoreRenderers, kompotStandardRenderers, generatedHaulAppRenderers.withActions())

private fun RenderersMap.withActions(): RenderersMap = mapValues { (_, renderer) -> ProvidingActions(renderer) }

private class ProvidingActions(
    renderer: KompotComponentRenderer<out KompotComponent>,
) : KompotComponentRenderer<KompotComponent> {
    // The registry looks a renderer up by the component's class, so the component is always the type
    // the wrapped renderer was registered for.
    @Suppress("UNCHECKED_CAST")
    private val inner = renderer as KompotComponentRenderer<KompotComponent>

    @Composable
    override fun Render(
        component: KompotComponent,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        CompositionLocalProvider(LocalHaulActions provides actionHandler) {
            inner.Render(component, actionHandler, formController)
        }
    }
}
