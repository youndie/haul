package io.github.youndie.haul.feature.about.screen

import io.github.youndie.haul.feature.about.domain.StackNumbers
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.ui.AboutBody
import io.github.youndie.haul.ui.AboutPoint
import io.github.youndie.haul.ui.AboutSection
import io.github.youndie.haul.ui.Link
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.OpenUrlAction
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * «The stack in numbers» (B-173, `GET /ui/numbers`, the page at `/numbers`): what this server was measured to cost —
 * start-up with and without its AOT cache, latency and CPU under the synthetic shoppers, memory inside the chart's
 * limit — with the day, the commit and how to measure it again. Drawn as the About page is (`AboutBody`), in the
 * frame, reached from it.
 *
 * Every number comes from [StackNumbers], the data file the measurement's reader writes from the raw runs; the copy
 * around them is fixed and names no number of its own, so nothing on the page is unmeasured (`NumbersRoutesTest`).
 */
internal object NumbersScreen {
    /** The address of the page, where the About page's «The stack in numbers» goes. */
    const val ADDRESS = "/numbers"

    /** The method, the pre-registration and the raw runs, as GitHub shows them. */
    const val METHOD = "${AboutScreen.REPOSITORY}/tree/main/docs/research/measurements/b-173"

    fun page(
        viewer: Viewer,
        navigation: List<Link>,
        numbers: StackNumbers = StackNumbers.measured,
    ): KompotComponent =
        Frame.page(
            "numbers-page",
            viewer,
            navigation,
            listOf(body(numbers)),
            Frame.title("The stack in numbers"),
            footer = true,
        )

    fun body(n: StackNumbers = StackNumbers.measured): AboutBody {
        val last = n.load.keys.maxBy { it.toInt() }
        val campaign = n.load.getValue(last)
        val earlier =
            n.load
                .filterKeys { it != last }
                .values
                .firstOrNull()
        return AboutBody(
            id = "numbers",
            eyebrow = "The stack in numbers",
            title = "What this server costs, measured",
            accent = "measured",
            lead =
                "Every number on this page was measured on ${n.measured}, on the image built from commit " +
                    "${n.commit.take(SHORT_SHA)}, on one shared Linux machine: the server held to two cores and the " +
                    "chart’s ${campaign.rq4.limitMib} MiB, PostgreSQL, the load and the synthetic shoppers on cores " +
                    "of their own. Nothing here is estimated; how to measure it again is at the end.",
            sections =
                listOf(
                    AboutSection("Start-up", startUp(n)),
                    AboutSection("Latency under load", latency(campaign, earlier)),
                    AboutSection("CPU per request", cpu(campaign, earlier)),
                    AboutSection("Memory", memory(campaign.rq4)),
                    AboutSection("How it was measured", METHOD_POINTS),
                ),
            links =
                listOf(
                    Link("Read the method and the raw runs", OpenUrlAction(METHOD)),
                    Link("About this demo", NavigateAction(AboutScreen.ADDRESS)),
                ),
        )
    }

    private fun startUp(n: StackNumbers): List<AboutPoint> {
        val ready = n.rq.rq1a
        val home = n.rq.rq1b
        val withCache =
            "Ready to serve ${ms(ready.aotMedianMs)} after docker run, the median of ${n.startup.rounds} starts; " +
                "the first home page at ${ms(home.aotMedianMs)}."
        val without =
            "${ms(ready.noAotMedianMs)} to ready and ${ms(home.noAotMedianMs)} to the first home page: the same " +
                "image, its cache switched off."
        val buys =
            "${ms(ready.savedMs)} (± ${ms(ready.savedHalfMs)}) to ready — ${percent(ready.savedShare)} of the start " +
                "without it — and ${ms(home.savedMs)} (± ${ms(home.savedHalfMs)}) to the first home page."
        val verdict =
            if (ready.verdict == "green" && home.verdict == "green") {
                "Green: distinguishable, past the lines set before the first run, and every start with the cache " +
                    "used it."
            } else {
                "${word(ready.verdict)} to ready, ${word(home.verdict)} to the first home page; the method says why."
            }
        return listOf(
            AboutPoint("With the AOT cache", withCache),
            AboutPoint("Without it", without),
            AboutPoint("What the cache buys", buys),
            AboutPoint("Docker’s own share", "${ms(n.startup.dockerRunMs)} of each start is docker run itself."),
            AboutPoint("Verdict", verdict),
        )
    }

    private fun latency(
        campaign: StackNumbers.Campaign,
        earlier: StackNumbers.Campaign?,
    ): List<AboutPoint> {
        val load = campaign.rq2
        val points = mutableListOf<AboutPoint>()
        points +=
            AboutPoint(
                "The load",
                "${load.rate} anonymous requests a second for ${load.rounds} rounds of ${load.seconds} s, the five " +
                    "routes below in turn, beside the synthetic shoppers walking the whole path.",
            )
        ROUTES.forEach { (key, name) ->
            val route = load.routes.getValue(key)
            points += AboutPoint(name, "${tenths(route.p50Ms)} ms at the median, ${tenths(route.p99Ms)} ms at p99.")
        }
        var verdict = "${word(load.verdict)} at ${load.rate} a second"
        if (!load.walkingEveryPair) {
            val stopped = load.roundWalksFinished.indexOfFirst { it == 0 }
            verdict +=
                ": every route inside its lines, but from the ${ORDINALS[stopped]} round on the shoppers’ checkouts " +
                "failed — the store’s delivery windows were full — so the load beside the requests was no " +
                "longer theirs"
        }
        if (earlier != null) {
            val fast = earlier.rq2
            // The rounds before the first that the server did not answer whole.
            val firstRounds = fast.roundDeliveredShare.indexOfFirst { it < 1.0 }.takeIf { it >= 0 } ?: fast.rounds
            val heldUnder = fast.routes.values.maxOf { it.roundP99Ms.take(firstRounds).max() }
            val slowest = fast.routes.values.maxOf { it.p99Ms }
            points +=
                AboutPoint(
                    "At twice the rate",
                    "${fast.rate} a second, the same morning, while other work loaded the machine: the first " +
                        "${WORDS[firstRounds]} rounds held p99 under ${whole(heldUnder)} ms, then the server fell " +
                        "behind — ${percent(fast.deliveredShare)} of the requests answered, p99 up to " +
                        "${whole(slowest)} ms.",
                )
            verdict += "; ${word(fast.verdict).lowercase()} at ${fast.rate}, on the loaded machine."
        } else {
            verdict += "."
        }
        points += AboutPoint("Verdict", verdict)
        return points
    }

    private fun cpu(
        campaign: StackNumbers.Campaign,
        earlier: StackNumbers.Campaign?,
    ): List<AboutPoint> {
        val cpu = campaign.rq3
        val server =
            "${thousandths(
                cpu.serverUsec,
            )} ms of CPU per request of that mix (± ${thousandths(cpu.serverHalfUsec)}), " +
                "over what the shoppers alone cost" +
                (
                    earlier?.let {
                        "; ${thousandths(it.rq3.serverUsec)} ms (± ${thousandths(it.rq3.serverHalfUsec)}) in the run " +
                            "at ${it.rq2.rate} a second"
                    } ?: ""
                ) + "."
        val verdict =
            "${word(cpu.verdict)} at ${campaign.rq2.rate} a second" +
                (earlier?.let { "; ${word(it.rq3.verdict).lowercase()} at ${it.rq2.rate}, its interval wider" } ?: "") +
                "."
        return listOf(
            AboutPoint("The server", server),
            AboutPoint(
                "PostgreSQL",
                "${thousandths(cpu.pgUsec)} ms more per request, in the database’s own container.",
            ),
            AboutPoint("Verdict", verdict),
        )
    }

    private fun memory(memory: StackNumbers.Memory): List<AboutPoint> {
        val points = mutableListOf<AboutPoint>()
        points +=
            AboutPoint(
                "At its peak",
                "${memory.peakMib} MiB of the ${memory.limitMib} MiB limit (${percent(memory.peakShare)}), as the " +
                    "container counts it, with the JVM’s heap sized from the limit; its resident set, shared files " +
                    "included, at most ${memory.vmrssMaxMib} MiB.",
            )
        if (memory.controlKilledMib != null) {
            points +=
                AboutPoint(
                    "Under a smaller limit",
                    "Killed — the JVM out of heap — under ${memory.controlKilledMib} MiB with the same load" +
                        (memory.controlLivedMib?.let { "; it lived under $it MiB." } ?: "."),
                )
        }
        val verdict =
            when (memory.verdict) {
                "green" -> "Green: inside the headroom set before the first run, never killed."
                "grey" -> "Grey: never killed and never restarted, but past the headroom set before the first run."
                else -> "${word(memory.verdict)}: the method says why."
            }
        points += AboutPoint("Verdict", verdict)
        return points
    }

    /** The routes the load asked for, by the data file's key, under the names the page gives them. */
    private val ROUTES =
        listOf(
            "home" to "The home page",
            "category" to "A category",
            "product" to "A product",
            "suggest" to "Search suggestions",
            "live" to "Live orders",
        )

    private val METHOD_POINTS =
        listOf(
            AboutPoint(
                "Pre-registered",
                "The questions, the lines a verdict is read against and the rules that stop a run were frozen before " +
                    "the first number; a check fails if a byte of them moves.",
            ),
            AboutPoint(
                "Interleaved",
                "The start-up arms took turns in every round and the first round was thrown away; a second arm with " +
                    "the cache is the ruler, and a start delayed by one second the control the harness must see.",
            ),
            AboutPoint(
                "Open-loop",
                "k6 offered requests at a fixed rate whatever the server answered, from cores the server did not " +
                    "have; CPU and memory are the container’s own counters, not the machine’s.",
            ),
            AboutPoint(
                "Again",
                "scripts/measure-stack.py — startup, then load — on Linux with Docker and k6; summarise turns " +
                    "the raw runs into this page’s data, and the check holds the page to them.",
            ),
        )

    private const val SHORT_SHA = 7

    private val WORDS = listOf("no", "one", "two", "three", "four", "five", "six", "seven")

    private val ORDINALS = listOf("first", "second", "third", "fourth", "fifth", "sixth", "seventh")

    private fun ms(value: Int): String = String.format(Locale.ROOT, "%,d ms", value)

    private fun whole(value: Double): String = String.format(Locale.ROOT, "%,d", value.roundToLong())

    private fun tenths(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

    private fun thousandths(micros: Int): String = String.format(Locale.ROOT, "%.1f", micros / 1000.0)

    private fun percent(share: Double): String = "${(share * 100).roundToInt()} %"

    private fun word(verdict: String): String =
        when (verdict) {
            "green" -> "Green"
            "grey" -> "Grey"
            "red" -> "Red"
            else -> "Not measured"
        }
}
