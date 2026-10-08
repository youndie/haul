package io.github.youndie.haul.ops

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * The orchestrator's three probes, outside every tier (endpoint-ops).
 *
 * Liveness touches nothing but the process: a database that is gone makes the server not ready, and
 * restarting it would not bring the database back. Readiness asks [ready], which checks the database.
 */
internal fun Route.probes(
    commit: String,
    ready: suspend () -> Boolean,
) {
    get("/healthz") { call.respondText("ok") }
    get("/readyz") {
        if (ready()) {
            call.respondText("ready")
        } else {
            call.respondText("not ready", status = HttpStatusCode.ServiceUnavailable)
        }
    }
    get("/version") { call.respondText("""{"commit":"$commit"}""", ContentType.Application.Json) }
}
