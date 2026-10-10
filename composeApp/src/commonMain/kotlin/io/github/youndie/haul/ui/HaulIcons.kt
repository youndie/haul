package io.github.youndie.haul.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The canvas's inline icons, path for path: a 24-unit box, stroked in the current colour (or
 * filled, where the canvas fills). Circles and rounded rectangles are written as the paths they are.
 */
public object HaulIcons {
    public val bag: ImageVector = stroked("bag", 2f, "M5 8h14l-1 12H6L5 8z", "M9 8V6a3 3 0 0 1 6 0v2")
    public val heart: ImageVector = stroked("heart", 2f, HEART)

    /** The heart of a saved product: filled and stroked in one colour, as the canvas draws it (`Saved_*`). */
    public val heartFilled: ImageVector =
        builder("heart-filled")
            .apply {
                addPath(
                    pathData = addPathNodes(HEART),
                    fill = SolidColor(Color.Black),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }.build()

    public val person: ImageVector = stroked("person", 2f, circle(12f, 8f, 4f), "M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6")
    public val box: ImageVector = stroked("box", 2f, "M3 7l9-4 9 4v10l-9 4-9-4V7z", "M3 7l9 4 9-4M12 11v10")
    public val search: ImageVector = stroked("search", 2.4f, circle(11f, 11f, 7f), "M20 20l-3.5-3.5")
    public val chevronDown: ImageVector = stroked("chevron-down", 2.2f, "M6 9l6 6 6-6")
    public val plus: ImageVector = stroked("plus", 2.6f, "M12 5v14M5 12h14")
    public val arrowRight: ImageVector = stroked("arrow-right", 2.4f, "M5 12h14M13 6l6 6-6 6")
    public val check: ImageVector = stroked("check", 2.6f, "M5 12l5 5L20 7")
    public val checkBold: ImageVector = stroked("check-bold", 3f, "M5 12l5 5L20 7")
    public val close: ImageVector = stroked("close", 2.6f, "M6 6l12 12M18 6L6 18")
    public val retry: ImageVector = stroked("retry", 2.4f, "M20 11a8 8 0 1 0-2.3 5.7", "M20 4v7h-7")
    public val clock: ImageVector = stroked("clock", 2.2f, circle(12f, 12f, 8f), "M12 8v4l3 2")
    public val alert: ImageVector = stroked("alert", 2.4f, circle(12f, 12f, 9f), "M12 7.5v5.5M12 16.5v.2")
    public val lock: ImageVector =
        stroked("lock", 2.2f, roundedRect(5f, 11f, 14f, 10f, 2f), "M8 11V8a4 4 0 0 1 8 0v3")
    public val pin: ImageVector =
        stroked("pin", 2f, "M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21z", circle(12f, 9.5f, 2.5f))

    /** The spinner's track and its arc, drawn apart because the canvas strokes them in two colours. */
    public val spinnerTrack: ImageVector = stroked("spinner-track", 3f, circle(12f, 12f, 9f))
    public val spinnerArc: ImageVector = stroked("spinner-arc", 3f, "M12 3a9 9 0 0 1 9 9")
    public val filters: ImageVector = stroked("filters", 2.4f, "M4 6h16M7 12h10M10 18h4")

    /** The phone header's menu (B-73): the canvas draws none, so it is the filters glyph's stroke, three equal bars. */
    public val menu: ImageVector = stroked("menu", 2.2f, "M4 6h16M4 12h16M4 18h16")
    public val share: ImageVector = stroked("share", 2f, "M12 15V3M7 8l5-5 5 5M5 14v6h14v-6")
    public val chevronRight: ImageVector = stroked("chevron-right", 2.4f, "M9 6l6 6-6 6")
    public val bolt: ImageVector = filled("bolt", "M13 2L4 14h7l-1 8 9-12h-7l1-8z")
    public val star: ImageVector =
        filled("star", "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1-4.4-4.3 6.1-.9z")
    public val grid: ImageVector =
        filled(
            "grid",
            roundedRect(3f, 3f, 8f, 8f, 2f),
            roundedRect(13f, 3f, 8f, 8f, 2f),
            roundedRect(3f, 13f, 8f, 8f, 2f),
            circle(17f, 17f, 4f),
        )

    private fun stroked(
        name: String,
        width: Float,
        vararg paths: String,
    ): ImageVector =
        builder(name)
            .apply {
                paths.forEach {
                    addPath(
                        pathData = addPathNodes(it),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = width,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }.build()

    private fun filled(
        name: String,
        vararg paths: String,
    ): ImageVector =
        builder(
            name,
        ).apply { paths.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) } }.build()

    private fun builder(name: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)

    private const val HEART = "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"

    private fun circle(
        cx: Float,
        cy: Float,
        r: Float,
    ): String = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

    private fun roundedRect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        r: Float,
    ): String =
        "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} $r" +
            "h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"
}
