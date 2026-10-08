package io.github.youndie.haul

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer

/**
 * The server's entry point. It routes nothing yet: every route arrives with the feature that owns
 * it, and the probes with the image (B-03).
 */
public fun main() {
    val port = System.getenv("PORT")?.toInt() ?: DEFAULT_PORT
    embeddedServer(CIO, port = port) {}.start(wait = true)
}

private const val DEFAULT_PORT = 8080
