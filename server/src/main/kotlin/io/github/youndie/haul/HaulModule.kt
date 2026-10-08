package io.github.youndie.haul

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.catalog.catalogModule
import io.github.youndie.haul.feature.catalog.catalogRouting
import io.github.youndie.haul.feature.catalog.domain.CatalogError
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.search.domain.SearchError
import io.github.youndie.haul.feature.search.searchModule
import io.github.youndie.haul.feature.search.searchRouting
import io.github.youndie.haul.ops.ObservabilitySettings
import io.github.youndie.haul.ops.installObservability
import io.github.youndie.haul.ops.probes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.staticFiles
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.slf4j.LoggerFactory
import java.io.File
import java.time.ZonedDateTime
import javax.sql.DataSource

/** «Now» for everything that shows a date, read in one place so a test can hold it still. */
internal fun interface StoreClock {
    fun now(): ZonedDateTime
}

/**
 * The application as both `main` and the tests assemble it: the agents that watch it, DI, error
 * answers, and every route under its tier. Nothing here reads the environment or the clock; [clock],
 * [dataSource], [observability] and [web] come in.
 *
 * [web] is the browser bundle's directory, served at `/` beside the API when given. Every API route is
 * more specific than the static one, so a screen route always wins; and there is deliberately no
 * fallback to `index.html` for a missing file — it would answer an unknown `/ui/...` with a page and
 * a 200 instead of the 404 the client draws.
 */
internal fun Application.haulModule(
    dataSource: DataSource,
    clock: StoreClock,
    commit: String,
    observability: ObservabilitySettings = ObservabilitySettings.NONE,
    web: File? = null,
) {
    val reportFailure = installObservability(observability)
    val database = Databases.connect(dataSource)
    install(Koin) {
        modules(
            module {
                single { database }
                single { dataSource }
                single { clock }
                single { DeliveryCalendar(clock::now) }
            },
            catalogModule,
            searchModule,
        )
    }
    install(StatusPages) {
        exception<CatalogError> { call, error ->
            call.respondText(
                haulWireJson.encodeToString(ErrorBody.serializer(), ErrorBody(error.code, error.message, error.field)),
                ContentType.Application.Json,
                status(error.code),
            )
        }
        exception<SearchError> { call, error ->
            call.respondText(
                haulWireJson.encodeToString(ErrorBody.serializer(), ErrorBody(error.code, error.message, error.field)),
                ContentType.Application.Json,
                status(error.code),
            )
        }
        exception<Throwable> { call, error ->
            log.error("unhandled", error)
            reportFailure(error)
            call.respondText(
                haulWireJson.encodeToString(
                    ErrorBody.serializer(),
                    ErrorBody(ErrorCode.Unavailable, "Something went wrong"),
                ),
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError,
            )
        }
    }
    routing {
        probes(commit = commit, ready = { databaseAnswers(dataSource) })
        catalogRouting()
        searchRouting()
        web?.let { staticFiles("/", it) }
    }
}

private val log = LoggerFactory.getLogger("io.github.youndie.haul.HaulModule")

internal fun status(code: ErrorCode): HttpStatusCode =
    when (code) {
        ErrorCode.ValidationFailed, ErrorCode.QueryTooShort -> HttpStatusCode.BadRequest
        ErrorCode.CategoryNotFound, ErrorCode.ProductNotFound -> HttpStatusCode.NotFound
        ErrorCode.Unavailable -> HttpStatusCode.ServiceUnavailable
    }
