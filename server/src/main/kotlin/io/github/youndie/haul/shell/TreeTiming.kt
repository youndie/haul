package io.github.youndie.haul.shell

import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.path
import io.ktor.util.AttributeKey
import java.util.Locale
import kotlin.time.DurationUnit
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * How long the server took over a tree (B-163): every answer under `/ui/` — a screen, its parts, the header alone,
 * the demo strip, the suggest panel — carries `Server-Timing: app;dur=<ms>`, from the moment the call reached the
 * application to the moment its answer was handed over, the tree built and encoded. The storefront's «Under the
 * hood» panel reads it beside the request's size and the time the browser waited, so a developer sees what the
 * server's share of a page was. Same origin, so the browser reads the header without `Timing-Allow-Origin`.
 *
 * Only `/ui/`: the commands under `/api/` and the page and bundle under `/` answer without it, so the header says
 * nothing about anything but the trees.
 */
internal val TreeTiming =
    createApplicationPlugin("TreeTiming") {
        onCall { call ->
            if (call.request.path().startsWith(TREES)) call.attributes.put(STARTED, TimeSource.Monotonic.markNow())
        }
        onCallRespond { call, _ ->
            // Taken, not read: an answer handed over twice — a refusal StatusPages answers after a handler began —
            // names its time once.
            val started = call.attributes.takeOrNull(STARTED) ?: return@onCallRespond
            call.response.headers.append(SERVER_TIMING, serverTiming(started))
        }
    }

/** The header's value for a call [started] then: one metric, `app`, its duration in milliseconds to a tenth. */
internal fun serverTiming(started: TimeMark): String {
    val millis = started.elapsedNow().toDouble(DurationUnit.MILLISECONDS)
    return "app;dur=" + String.format(Locale.ROOT, "%.1f", millis)
}

/** The header: the W3C Server Timing one, which the browser's network panel draws too. */
internal const val SERVER_TIMING: String = "Server-Timing"

private const val TREES = "/ui/"

private val STARTED = AttributeKey<TimeMark>("haul.treeTiming.started")
