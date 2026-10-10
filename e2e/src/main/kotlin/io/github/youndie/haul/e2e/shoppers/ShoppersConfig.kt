package io.github.youndie.haul.e2e.shoppers

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * What the synthetic shoppers are told by their environment (the chart's `shoppers` values, B-31). Every
 * address is the storefront's own: the origin the walks start at and the return page its sign-in client
 * registers. The people are logins of the realm's existing customers, all signing in with one [password]
 * — the stand's demo password, which the chart hands over from its Secret and nothing ever prints.
 *
 * @property interval between the starts of two walks: the rate, one walk per interval.
 * @property poll how often a walk reads its order's page again while it waits for the fulfilment clock.
 * @property wait how long a walk waits for its order to arrive, and again for its refund, before giving up.
 * @property inFlight walks at once at most; a start that would pass it is skipped, not queued.
 * @property walks walks to take before stopping, `0` for no end — what a stand runs.
 */
internal data class ShoppersConfig(
    val origin: String,
    val redirect: String,
    val people: List<String>,
    val password: Password,
    val interval: Duration = 600.seconds,
    val poll: Duration = 30.seconds,
    val wait: Duration = 3600.seconds,
    val inFlight: Int = 4,
    val walks: Int = 0,
) {
    companion object {
        const val ORIGIN = "HAUL_SHOPPERS_ORIGIN"
        const val REDIRECT = "HAUL_SHOPPERS_REDIRECT"
        const val PEOPLE = "HAUL_SHOPPERS_PEOPLE"
        const val PASSWORD = "HAUL_SHOPPERS_PASSWORD"
        const val INTERVAL = "HAUL_SHOPPERS_INTERVAL_SECONDS"
        const val POLL = "HAUL_SHOPPERS_POLL_SECONDS"
        const val WAIT = "HAUL_SHOPPERS_WAIT_SECONDS"
        const val IN_FLIGHT = "HAUL_SHOPPERS_IN_FLIGHT"
        const val WALKS = "HAUL_SHOPPERS_WALKS"

        /**
         * The configuration [env] describes, or [IllegalArgumentException] naming the variable that is
         * missing or wrong — never its value, since one of them is a password.
         */
        fun from(env: Map<String, String>): ShoppersConfig {
            fun required(name: String): String =
                env[name]?.trim()?.takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("$name is required")

            fun number(
                name: String,
                default: Int,
                least: Int,
            ): Int {
                val text = env[name]?.trim()?.takeIf { it.isNotEmpty() } ?: return default
                val value = text.toIntOrNull() ?: throw IllegalArgumentException("$name is not a whole number")
                require(value >= least) { "$name is $value, and must be at least $least" }
                return value
            }

            val origin = required(ORIGIN).trimEnd('/')
            require(origin.startsWith("http://") || origin.startsWith("https://")) {
                "$ORIGIN is not an http(s) origin: $origin"
            }
            val people = required(PEOPLE).split(',').map { it.trim() }.filter { it.isNotEmpty() }
            require(people.isNotEmpty()) { "$PEOPLE names nobody" }
            return ShoppersConfig(
                origin = origin,
                redirect = required(REDIRECT),
                people = people,
                password = Password(required(PASSWORD)),
                interval = number(INTERVAL, 600, least = 1).seconds,
                poll = number(POLL, 30, least = 1).seconds,
                wait = number(WAIT, 3600, least = 1).seconds,
                inFlight = number(IN_FLIGHT, 4, least = 1),
                walks = number(WALKS, 0, least = 0),
            )
        }
    }
}

/** A password that says nothing when printed, so a configuration logged whole leaks nothing. */
@JvmInline
internal value class Password(
    val value: String,
) {
    override fun toString(): String = "<hidden>"
}
