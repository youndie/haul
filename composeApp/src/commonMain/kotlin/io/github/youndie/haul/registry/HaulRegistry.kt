package io.github.youndie.haul.registry

import io.github.youndie.kompot.KompotRegistry
import io.github.youndie.kompot.generated.generatedHaulAppRenderers
import io.github.youndie.kompot.generated.generatedHaulContractSerializersModule
import io.github.youndie.kompot.kompotCoreRenderers
import io.github.youndie.kompot.kompotJson
import io.github.youndie.kompot.kompotStandardRenderers
import kotlinx.serialization.json.Json

/** What a body from the server is decoded with: kompot's own types plus every Haul component. */
public val haulJson: Json = kompotJson(generatedHaulContractSerializersModule)

/**
 * Every renderer the storefront has: kompot's core and standard sets (the page is a `column`) and one
 * per Haul component.
 */
public fun haulRegistry(): KompotRegistry =
    KompotRegistry(kompotCoreRenderers, kompotStandardRenderers, generatedHaulAppRenderers)
