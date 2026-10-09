package io.github.youndie.haul.feature.catalog.domain

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

// The way the canvas writes numbers. The server formats; the client only draws (research D2).

private val GROUPED = NumberFormat.getIntegerInstance(Locale.US)

/** «$349» for whole dollars, «$87.25» otherwise. */
internal fun money(cents: Int): String {
    val dollars = "$" + GROUPED.format(cents / 100)
    return if (cents % 100 == 0) dollars else dollars + ".%02d".format(cents % 100)
}

/** «2,341». */
internal fun count(value: Int): String = GROUPED.format(value)

/** «−22%», with the canvas's minus sign; `null` when there is no old price. */
internal fun discount(
    priceCents: Int,
    oldCents: Int?,
): String? = oldCents?.takeIf { it > priceCents }?.let { "−" + ((it - priceCents) * 100.0 / it).roundToInt() + "%" }

/**
 * A count the way the canvas abbreviates it (B-52): «840», «1.2K», «12K», «600K», «1.2M». Below a thousand
 * the number itself; under ten of a unit one decimal, a trailing «.0» dropped; from ten whole units.
 * Always truncated, never rounded: 12,999 is «12K» and 999 is «999», so the label never claims a unit
 * more than there is.
 */
internal fun compactCount(value: Int): String {
    require(value >= 0) { "a count is never negative: $value" }
    val (unit, suffix) =
        when {
            value >= MILLION -> MILLION to "M"
            value >= THOUSAND -> THOUSAND to "K"
            else -> return value.toString()
        }
    val whole = value / unit
    if (whole >= 10) return "$whole$suffix"
    val tenth = value % unit / (unit / 10)
    return if (tenth == 0) "$whole$suffix" else "$whole.$tenth$suffix"
}

private const val THOUSAND = 1_000
private const val MILLION = 1_000_000
