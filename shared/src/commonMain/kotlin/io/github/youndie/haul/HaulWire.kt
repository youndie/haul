package io.github.youndie.haul

import io.github.youndie.kompot.commands.kompotCommandsSerializersModule
import io.github.youndie.kompot.generated.generatedHaulContractSerializersModule
import io.github.youndie.kompot.generated.generatedStandardSerializersModule
import io.github.youndie.kompot.kompotCoreSerializersModule
import io.github.youndie.kompot.standard.kompotStandardSerializersModule
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.plus

/**
 * The JSON every tree and every error travels in: kompot's own types, its standard containers, its
 * commands — `load` in a tree, `update` in an answer (B-63) — and the Haul components. The server
 * encodes with it; the client decodes with kompot's engine module plus the same Haul module, which is
 * the same set since kompot 0.40.0.213.
 */
public val haulWireJson: Json =
    Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        explicitNulls = false
        serializersModule =
            kompotCoreSerializersModule +
            kompotStandardSerializersModule +
            kompotCommandsSerializersModule +
            generatedStandardSerializersModule +
            generatedHaulContractSerializersModule
    }
