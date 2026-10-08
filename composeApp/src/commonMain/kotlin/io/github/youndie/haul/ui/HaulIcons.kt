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
    public val heart: ImageVector =
        stroked("heart", 2f, "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z")
    public val person: ImageVector = stroked("person", 2f, circle(12f, 8f, 4f), "M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6")
    public val box: ImageVector = stroked("box", 2f, "M3 7l9-4 9 4v10l-9 4-9-4V7z", "M3 7l9 4 9-4M12 11v10")
    public val search: ImageVector = stroked("search", 2.4f, circle(11f, 11f, 7f), "M20 20l-3.5-3.5")
    public val chevronDown: ImageVector = stroked("chevron-down", 2.2f, "M6 9l6 6 6-6")
    public val plus: ImageVector = stroked("plus", 2.6f, "M12 5v14M5 12h14")
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
