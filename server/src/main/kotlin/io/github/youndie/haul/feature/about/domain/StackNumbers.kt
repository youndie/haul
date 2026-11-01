package io.github.youndie.haul.feature.about.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What B-173 measured of this server (`docs/research/measurements/b-173/`): start-up with and without the AOT cache,
 * latency, CPU and memory under the synthetic shoppers — read from the data file `scripts/measure-stack.py summarise`
 * writes from the raw runs (`numbers/stack-numbers.json`), never typed in. `make check` holds the file to the raw runs
 * (`summarise --check`), and the page «The stack in numbers» writes nothing that is not in it.
 *
 * Only the fields the page draws are read; the file carries more (the intervals' inputs, the controls), which the
 * research reads.
 */
@Serializable
internal data class StackNumbers(
    /** The day the runs were taken, `2026-11-01`. */
    val measured: String,
    /** The commit the measured image was built from. */
    val commit: String,
    val rq: Questions,
    val startup: Startup,
    /** The load block's campaigns by their number, `"1"` first: each a run of the whole block, as `raw/load-<n>/`. */
    val load: Map<String, Campaign>,
) {
    @Serializable
    data class Questions(
        val rq1a: StartUp,
        val rq1b: StartUp,
    )

    /** One run of the load block: RQ2-RQ4 with the box's load beside them. */
    @Serializable
    data class Campaign(
        val rq2: Latency,
        val rq3: Cpu,
        val rq4: Memory,
    )

    /** One start-up question: the medians of each arm and the paired difference, in milliseconds. */
    @Serializable
    data class StartUp(
        val verdict: String,
        @SerialName("aot_median_ms") val aotMedianMs: Int,
        @SerialName("no_aot_median_ms") val noAotMedianMs: Int,
        @SerialName("saved_ms") val savedMs: Int,
        @SerialName("saved_half_ms") val savedHalfMs: Int,
        @SerialName("saved_share") val savedShare: Double,
    )

    @Serializable
    data class Startup(
        val rounds: Int,
        @SerialName("docker_run_ms") val dockerRunMs: Int,
    )

    @Serializable
    data class Latency(
        val verdict: String,
        val rate: Int,
        val rounds: Int,
        val seconds: Int,
        val requests: Int,
        @SerialName("walks_finished") val walksFinished: Int,
        @SerialName("delivered_share") val deliveredShare: Double,
        @SerialName("walking_every_pair") val walkingEveryPair: Boolean,
        @SerialName("round_walks_finished") val roundWalksFinished: List<Int>,
        @SerialName("round_delivered_share") val roundDeliveredShare: List<Double>,
        val routes: Map<String, Route>,
    )

    @Serializable
    data class Route(
        @SerialName("p50_ms") val p50Ms: Double,
        @SerialName("p99_ms") val p99Ms: Double,
        @SerialName("server_p50_ms") val serverP50Ms: Double? = null,
        /** The p99 of each counted round, in the order they ran. */
        @SerialName("round_p99_ms") val roundP99Ms: List<Double>,
    )

    @Serializable
    data class Cpu(
        val verdict: String,
        @SerialName("server_usec") val serverUsec: Int,
        @SerialName("server_half_usec") val serverHalfUsec: Int,
        @SerialName("pg_usec") val pgUsec: Int,
    )

    @Serializable
    data class Memory(
        val verdict: String,
        @SerialName("limit_mib") val limitMib: Int,
        @SerialName("peak_mib") val peakMib: Int,
        @SerialName("peak_share") val peakShare: Double,
        @SerialName("vmrss_max_mib") val vmrssMaxMib: Int,
        @SerialName("control_killed_mib") val controlKilledMib: Int? = null,
        @SerialName("control_lived_mib") val controlLivedMib: Int? = null,
    )

    companion object {
        /** Where the data file is on the classpath. */
        const val RESOURCE = "numbers/stack-numbers.json"

        private val json = Json { ignoreUnknownKeys = true }

        /** The measured numbers, read once; a server built without them does not start serving a page that lies. */
        val measured: StackNumbers by lazy {
            val text =
                checkNotNull(
                    StackNumbers::class.java.classLoader.getResource(RESOURCE),
                ) { "no $RESOURCE on the classpath" }
                    .readText()
            json.decodeFromString(serializer(), text)
        }
    }
}
