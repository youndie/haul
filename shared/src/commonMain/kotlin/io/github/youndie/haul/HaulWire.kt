package io.github.youndie.haul

import io.github.youndie.kompot.generated.generatedHaulContractSerializersModule
import io.github.youndie.kompot.generated.generatedStandardSerializersModule
import io.github.youndie.kompot.kompotCoreSerializersModule
import io.github.youndie.kompot.standard.kompotStandardSerializersModule
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.plus

/**
 * The JSON every tree and every error travels in: kompot's own types, its standard containers, and
 * the Haul components. The server encodes with it; the client decodes with kompot's engine module
 * plus the same Haul module, which is the same set.
 */
public val haulWireJson: Json =
    Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        explicitNulls = false
        serializersModule =
            kompotCoreSerializersModule +
            kompotStandardSerializersModule +
            generatedStandardSerializersModule +
            generatedHaulContractSerializersModule
    }
