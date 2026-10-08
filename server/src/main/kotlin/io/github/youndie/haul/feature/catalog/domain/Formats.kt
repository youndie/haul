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
