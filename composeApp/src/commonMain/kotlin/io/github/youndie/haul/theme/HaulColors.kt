package io.github.youndie.haul.theme

import androidx.compose.ui.graphics.Color

/**
 * The canvas's colour roles (`canvas/canvas.json`, `roles`), named as the canvas names them. A colour
 * a renderer needs that is not here is a question for the designer, not a literal in the renderer.
 */
public object HaulColors {
    /** Cobalt: actions, links, selection. */
    public val primary: Color = Color(0xFF2F2BFF)
    public val onPrimary: Color = Color(0xFFFFFFFF)

    /** Acid: discount badges, highlights, counts. */
    public val secondaryContainer: Color = Color(0xFFDFFF3A)
    public val onSecondaryContainer: Color = Color(0xFF0F0F0F)

    /** Hot: promo fills, the saved heart, «Price dropped». */
    public val tertiaryContainer: Color = Color(0xFFFF3D2E)

    /** Sale red: «Deals», discount amounts, field errors. */
    public val error: Color = Color(0xFFD9230F)
    public val errorContainer: Color = Color(0xFFFFE5DD)

    /** Paper: the page. */
    public val background: Color = Color(0xFFF5F3EE)
    public val surfaceContainerLowest: Color = Color(0xFFFFFFFF)
    public val surfaceContainerHigh: Color = Color(0xFFE9E6E0)
    public val surfaceContainerHighest: Color = Color(0xFFD6D1C7)

    /** Ink: text and icons. */
    public val onSurface: Color = Color(0xFF0F0F0F)
    public val onSurfaceVariant: Color = Color(0xFF3A3833)
    public val outline: Color = Color(0xFF5E5B55)
    public val outlineMuted: Color = Color(0xFF6F6B64)
    public val outlineVariant: Color = Color(0xFFE2DED6)
    public val outlineControl: Color = Color(0xFFC9C4BA)

    public val inverseSurface: Color = Color(0xFF0F0F0F)
    public val inverseOnSurface: Color = Color(0xFFF5F3EE)
    public val inverseOnSurfaceVariant: Color = Color(0xFFBDB8AE)
    public val scrim: Color = Color(0x800F0F0F)

    /** A control on the inverse surface: white at 12 % (the Plus trial dialog's «×», `Home_PlusTrialDialog`). */
    public val inverseControl: Color = Color(0x1FFFFFFF)

    /**
     * A floating panel's shadow, black at 45 % (Search_Autocomplete's `box-shadow`). `canvas.json` lists
     * no role for it: the canvas's own shadows are written inline.
     */
    public val shadow: Color = Color(0x73000000)

    /** The placeholder photo tile's hatching: Ink at 5 %. */
    public val tileHatch: Color = Color(0x0D0F0F0F)

    /** The placeholder tile's label: Ink at 50 %. */
    public val tileLabel: Color = Color(0x800F0F0F)
}
