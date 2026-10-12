package io.github.youndie.haul.seed

/**
 * The few SVG elements the seed's pictures are drawn with ([SeedArt]): flat fills, round-capped strokes,
 * groups that move and turn what they hold. Numbers are written with at most one decimal and a dot whatever
 * the locale, so the same picture is the same bytes on every machine — its address carries their hash.
 */
internal class Svg(
    private val width: Int,
    private val height: Int,
) {
    private val defs = StringBuilder()
    private val body = StringBuilder()

    fun rect(
        x: Number,
        y: Number,
        w: Number,
        h: Number,
        fill: String,
        rx: Number = 0,
        opacity: Double = 1.0,
    ) = element("rect", "x" to x, "y" to y, "width" to w, "height" to h, "rx" to rx.takeIf { it.toDouble() > 0 }) {
        fill(fill, opacity)
    }

    fun circle(
        cx: Number,
        cy: Number,
        r: Number,
        fill: String,
        opacity: Double = 1.0,
    ) = element("circle", "cx" to cx, "cy" to cy, "r" to r) { fill(fill, opacity) }

    fun ellipse(
        cx: Number,
        cy: Number,
        rx: Number,
        ry: Number,
        fill: String,
        opacity: Double = 1.0,
    ) = element("ellipse", "cx" to cx, "cy" to cy, "rx" to rx, "ry" to ry) { fill(fill, opacity) }

    /** A filled outline, [d] in SVG's path syntax. */
    fun path(
        d: String,
        fill: String,
        opacity: Double = 1.0,
    ) = element("path", "d" to d) { fill(fill, opacity) }

    /** An unfilled line along [d], [width] wide with round ends. */
    fun stroke(
        d: String,
        colour: String,
        width: Number,
        opacity: Double = 1.0,
    ) = element("path", "d" to d) {
        append(" fill=\"none\" stroke=\"").append(colour).append("\" stroke-width=\"").append(num(width))
        append("\" stroke-linecap=\"round\" stroke-linejoin=\"round\"")
        if (opacity < 1.0) append(" opacity=\"").append(num(opacity, 2)).append('"')
    }

    /** A ring: a circle's outline. */
    fun ring(
        cx: Number,
        cy: Number,
        r: Number,
        colour: String,
        width: Number,
        opacity: Double = 1.0,
    ) = element("circle", "cx" to cx, "cy" to cy, "r" to r) {
        append(" fill=\"none\" stroke=\"")
            .append(colour)
            .append("\" stroke-width=\"")
            .append(num(width))
            .append('"')
        if (opacity < 1.0) append(" opacity=\"").append(num(opacity, 2)).append('"')
    }

    /** [content] moved by ([x], [y]), scaled by [scale] and turned by [degrees] about the origin it is drawn around. */
    fun group(
        x: Number = 0,
        y: Number = 0,
        scale: Number = 1,
        degrees: Number = 0,
        content: Svg.() -> Unit,
    ) {
        val moves =
            buildList {
                if (x.toDouble() != 0.0 || y.toDouble() != 0.0) add("translate(${num(x)} ${num(y)})")
                if (degrees.toDouble() != 0.0) add("rotate(${num(degrees)})")
                if (scale.toDouble() != 1.0) add("scale(${num(scale, 3)})")
            }
        body.append("<g")
        if (moves.isNotEmpty()) body.append(" transform=\"").append(moves.joinToString(" ")).append('"')
        body.append('>')
        content()
        body.append("</g>")
    }

    /**
     * The canvas's placeholder hatching (`repeating-linear-gradient(135deg, …)`): [colour] hairlines across the
     * 135° axis every [period] units, over whatever [fill] covers.
     */
    fun hatching(
        colour: String,
        opacity: Double,
        period: Number,
    ) {
        val id = "h"
        if ("id=\"$id\"" !in defs) {
            defs
                .append("<pattern id=\"")
                .append(id)
                .append("\" width=\"")
                .append(num(period))
            defs.append("\" height=\"").append(num(period))
            defs.append("\" patternUnits=\"userSpaceOnUse\" patternTransform=\"rotate(45)\">")
            defs
                .append("<rect width=\"1\" height=\"")
                .append(num(period))
                .append("\" fill=\"")
                .append(colour)
            defs.append("\" opacity=\"").append(num(opacity, 2)).append("\"/></pattern>")
        }
        body
            .append("<rect width=\"")
            .append(width)
            .append("\" height=\"")
            .append(height)
        body.append("\" fill=\"url(#").append(id).append(")\"/>")
    }

    override fun toString(): String =
        buildString {
            append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(width).append(' ').append(height)
            append("\" width=\"")
                .append(width)
                .append("\" height=\"")
                .append(height)
                .append("\">")
            if (defs.isNotEmpty()) append("<defs>").append(defs).append("</defs>")
            append(body)
            append("</svg>")
        }

    private fun element(
        name: String,
        vararg attributes: Pair<String, Any?>,
        paint: StringBuilder.() -> Unit,
    ) {
        body.append('<').append(name)
        attributes.forEach { (key, value) ->
            if (value == null) return@forEach
            body
                .append(' ')
                .append(key)
                .append("=\"")
                .append(if (value is Number) num(value) else value)
                .append('"')
        }
        body.paint()
        body.append("/>")
    }

    private fun StringBuilder.fill(
        colour: String,
        opacity: Double,
    ) {
        append(" fill=\"").append(colour).append('"')
        if (opacity < 1.0) append(" opacity=\"").append(num(opacity, 2)).append('"')
    }

    companion object {
        /** [value] with at most [decimals] decimals, trailing zeros dropped, a dot as the separator. */
        fun num(
            value: Number,
            decimals: Int = 1,
        ): String {
            var factor = 1L
            repeat(decimals) { factor *= 10 }
            val scaled = Math.round(value.toDouble() * factor)
            val sign = if (scaled < 0) "-" else ""
            val whole = Math.abs(scaled) / factor
            val fraction = (Math.abs(scaled) % factor).toString().padStart(decimals, '0').trimEnd('0')
            return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole.$fraction"
        }
    }
}
