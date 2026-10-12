package io.github.youndie.haul.ops

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.filter.ThresholdFilter
import io.github.youndie.katcher.Katcher
import io.github.youndie.metrik.agent.Metrik
import io.github.youndie.tracy.agent.AgentConfig
import io.github.youndie.tracy.agent.Tracy
import io.github.youndie.tracy.agent.TracyAgent
import io.github.youndie.tracy.agent.TracyDelivery
import io.github.youndie.tracy.agent.TracyLogbackAppender
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

/** Where one agent sends, and the key it sends with. The key never reaches a log line. */
internal class AgentEndpoint(
    val endpoint: String,
    val key: String,
) {
    override fun toString(): String = "AgentEndpoint(endpoint=$endpoint)"
}

/**
 * Who watches this server (B-27): metrik, tracy and katcher, each on when its endpoint and key are
 * both given and off otherwise.
 *
 * [release] is what a crash group and a deploy marker are named after — the commit the image was
 * built from. [instance] is the pod's name, so a record leads back to one lifetime of the process.
 */
internal class ObservabilitySettings(
    val release: String,
    val environment: String,
    val instance: String,
    val metrik: AgentEndpoint? = null,
    val tracy: AgentEndpoint? = null,
    val tracySampleRate: Double? = null,
    val katcher: AgentEndpoint? = null,
) {
    internal companion object {
        /** What the tests and a laptop run with: nothing installed, nothing sent. */
        val NONE: ObservabilitySettings =
            ObservabilitySettings(release = "dev", environment = "local", instance = "local")
    }
}

/** The service name every agent files this server under; there is no registration, so it is spelled once. */
internal const val SERVICE: String = "haul-server"

private val log = LoggerFactory.getLogger("io.github.youndie.haul.ops.Observability")

private val FLUSH_GRACE = 3.seconds

/**
 * What the installed agents give the rest of the server: [reportFailure] to call with an exception the server
 * answered 500 for — katcher's report, or nothing — and [trail], where an order's saga and simulators write
 * what happened to it — tracy, or [OrderTrail.NONE].
 */
internal class Observers(
    val reportFailure: (Throwable) -> Unit,
    val trail: OrderTrail,
)

/**
 * Installs the agents [settings] names, and says at `warn` which ones it did not: a server that sends
 * nothing and a server nobody uses draw the same empty dashboard.
 *
 * Installed before routing, because tracy's trace context is what a span inside a handler attaches to.
 * tracy goes first of the three: from then on a `warn` reaches it ([shipWarnings]), so the lines below
 * saying metrik or katcher is off are read in tracy, not only in the pod's log (B-122).
 * The buffered records leave on `ApplicationStopping` — the ones explaining a shutdown are the least
 * replaceable there are, and tracy 0.2 and katcher flush only when asked.
 *
 * Returns what the rest of the server writes to them through ([Observers]).
 */
internal fun Application.installObservability(settings: ObservabilitySettings): Observers {
    val agent = settings.tracy?.let { installTracy(it, settings) }
    val delivery = agent?.delivery
    if (delivery == null) log.warn("tracy is off (HAUL_TRACY_ENDPOINT/HAUL_TRACY_KEY): no trace, no shipped log")

    val metrik = settings.metrik
    if (metrik != null) {
        install(Metrik) {
            service = SERVICE
            apiKey = metrik.key
            endpoint = metrik.endpoint
            instanceId = settings.instance
            release = settings.release
        }
    } else {
        log.warn("metrik is off (HAUL_METRIK_ENDPOINT/HAUL_METRIK_KEY): nothing counts the routes")
    }

    val katcher = settings.katcher
    if (katcher != null) {
        Katcher.start {
            appKey = katcher.key
            remoteHost = katcher.endpoint
            release = settings.release
            environment = settings.environment
            cacheDir = System.getProperty("java.io.tmpdir") + "/katcher"
        }
    } else {
        log.warn("katcher is off (HAUL_KATCHER_ENDPOINT/HAUL_KATCHER_KEY): a 500 is a log line and nothing else")
    }

    if (delivery != null || katcher != null) {
        monitor.subscribe(ApplicationStopping) {
            runBlocking {
                delivery?.let { launch { it.stop(FLUSH_GRACE) } }
                if (katcher != null) launch { Katcher.flush(FLUSH_GRACE) }
            }
        }
    }
    return Observers(
        reportFailure = if (katcher != null) { error -> Katcher.catch(error) } else { _ -> },
        trail = agent?.let { TracyOrderTrail(it.agent) } ?: OrderTrail.NONE,
    )
}

/** tracy's agent, which records are written to, and the loop that delivers them. */
private class InstalledTracy(
    val agent: TracyAgent,
    val delivery: TracyDelivery,
)

/**
 * tracy's server plugin and its delivery loop. The sample rate is the agent's own (1 %) unless the
 * deployment names one: tail sampling keeps failures and slow calls, so a stand whose every request
 * is fast and successful shows an empty collector at 1 % and reads as broken wiring.
 */
@Suppress(
    "ktlint:kapkan:wall-clock",
    "A record's timestamp is the agent's to stamp, in the epoch milliseconds its collector expects; nothing in the store's own time reads it.",
)
private fun Application.installTracy(
    tracy: AgentEndpoint,
    settings: ObservabilitySettings,
): InstalledTracy {
    val rate = settings.tracySampleRate
    val config =
        if (rate == null) {
            AgentConfig(SERVICE, tracy.key, tracy.endpoint, instanceId = settings.instance, release = settings.release)
        } else {
            AgentConfig(
                SERVICE,
                tracy.key,
                tracy.endpoint,
                instanceId = settings.instance,
                release = settings.release,
                sampleRate = rate,
            )
        }
    val agent = TracyAgent(config, clock = { System.currentTimeMillis() })
    install(Tracy) { this.agent = agent }
    shipWarnings(agent)
    return InstalledTracy(agent, TracyDelivery(agent, config).also { it.start(this) })
}

/**
 * Every SLF4J line at [SHIPPED_FROM] and above, whoever logs it, handed to tracy as well as to stdout (B-122).
 *
 * tracy's agent captures nothing on its own on the JVM: its plugin carries spans and what is written through
 * its API, and the logback appender it ships is the host's to attach. Unattached, the server's every
 * `log.warn` and `log.error` — the start-up lines saying what is off, an unhandled failure, a saga that gave
 * up — stayed in the pod's log, and tracy showed none of them.
 *
 * From `warn`, not from the agent's `info`: a captured line carries no trace (SLF4J has none to give), so it
 * skips tail sampling and is sent whole — at `info` that is every refused request's line (`ErrorAnswers`)
 * at the shoppers' rate. A `warn` is what tracy keeps from every request anyway.
 *
 * Detached when the application has stopped: the logger context outlives an application, and in a test JVM
 * every application started with tracy would otherwise leave one more appender feeding a stopped agent.
 */
private fun Application.shipWarnings(agent: TracyAgent) {
    val context = LoggerFactory.getILoggerFactory() as? LoggerContext ?: return
    val threshold =
        ThresholdFilter().apply {
            setLevel(SHIPPED_FROM.levelStr)
            this.context = context
            start()
        }
    val appender =
        TracyLogbackAppender(agent).apply {
            name = "tracy"
            this.context = context
            addFilter(threshold)
            start()
        }
    val root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
    root.addAppender(appender)
    monitor.subscribe(ApplicationStopped) {
        root.detachAppender(appender)
        appender.stop()
    }
}

/** The level a line logged through SLF4J must have to be shipped to tracy ([shipWarnings]). */
private val SHIPPED_FROM: Level = Level.WARN
