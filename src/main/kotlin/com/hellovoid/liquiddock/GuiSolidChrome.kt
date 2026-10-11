package com.hellovoid.liquiddock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp

/**
 * All flat chrome, including the GUI canvas, is predetermined for light/dark themes.
 * Color resources store the final fill/rim/outline values relative to the same canvas.
 * The scaffold resolves each resource once, not once for each scrolling Cell.
 */
internal data class GuiPresetChromePalette(
    val canvas: Color,
    val cardFill: Color,
    val cardRim: Color,
    val cardOutline: Color,
    val pillFill: Color,
    val pillRim: Color,
    val pillOutline: Color,
    val dangerFill: Color,
    val dangerRim: Color,
    val dangerOutline: Color,
)

internal val LocalGuiPresetChromePalette = staticCompositionLocalOf<GuiPresetChromePalette> {
    error("Gui preset chrome requires ModernSettingsScaffold")
}

@Composable
internal fun rememberGuiPresetChromePalette(): GuiPresetChromePalette {
    // Android picks values/ or values-night/ once for the current system theme.
    // Reading each static color here avoids luminance and per-cell color computations.
    val canvas = colorResource(R.color.gui_preset_canvas)
    val cardFill = colorResource(R.color.gui_preset_card_fill)
    val cardRim = colorResource(R.color.gui_preset_card_rim)
    val cardOutline = colorResource(R.color.gui_preset_card_outline)
    val pillFill = colorResource(R.color.gui_preset_pill_fill)
    val pillRim = colorResource(R.color.gui_preset_pill_rim)
    val pillOutline = colorResource(R.color.gui_preset_pill_outline)
    val dangerFill = colorResource(R.color.gui_preset_danger_fill)
    val dangerRim = colorResource(R.color.gui_preset_danger_rim)
    val dangerOutline = colorResource(R.color.gui_preset_danger_outline)
    return remember(
        canvas, cardFill, cardRim, cardOutline,
        pillFill, pillRim, pillOutline,
        dangerFill, dangerRim, dangerOutline,
    ) {
        GuiPresetChromePalette(
            canvas, cardFill, cardRim, cardOutline,
            pillFill, pillRim, pillOutline,
            dangerFill, dangerRim, dangerOutline,
        )
    }
}

internal enum class GuiSolidChromeStyle { CARD, PILL, DANGER }

private val cardShape = RoundedCornerShape(24.dp)
private val pillShape = RoundedCornerShape(999.dp)

/**
 * A direct solid fill and two fixed-color outlines; no extra composable, drawable
 * inflation, shader gradient, dynamic light or backdrop capture. Both outlines
 * use precomputed color resources, not runtime color interpolation.
 */
internal fun Modifier.guiSolidChrome(
    style: GuiSolidChromeStyle,
    palette: GuiPresetChromePalette,
): Modifier {
    // Select resource values without allocating a Triple or a Paint per Cell.
    val fill: Color
    val rim: Color
    val outline: Color
    when (style) {
        GuiSolidChromeStyle.CARD -> {
            fill = palette.cardFill
            rim = palette.cardRim
            outline = palette.cardOutline
        }
        GuiSolidChromeStyle.PILL -> {
            fill = palette.pillFill
            rim = palette.pillRim
            outline = palette.pillOutline
        }
        GuiSolidChromeStyle.DANGER -> {
            fill = palette.dangerFill
            rim = palette.dangerRim
            outline = palette.dangerOutline
        }
    }
    val shape = if (style == GuiSolidChromeStyle.CARD) cardShape else pillShape
    return this
        .background(fill, shape)
        .border(1.dp, outline, shape)
        .border(0.5.dp, rim, shape)
}
