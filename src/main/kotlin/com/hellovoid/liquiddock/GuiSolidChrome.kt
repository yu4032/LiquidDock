package com.hellovoid.liquiddock

import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/**
 * The settings canvas and opaque material fills are fixed per light/dark theme.
 * Directional white specular distribution is pre-baked into reusable 9-patch
 * resources. Unlike uniform stroke colors, its intensity decays from the
 * upper-left key light to the dark right/bottom perimeter.
 */
internal data class GuiPresetChromePalette(
    val canvas: Color,
    val canvasWithoutGlass: Color,
    val cardFill: Color,
    val pillFill: Color,
    val thumbFill: Color,
    val dangerFill: Color,
    val cardSpecular: Drawable,
    val pillSpecular: Drawable,
    val thumbSpecular: Drawable,
)

internal val LocalGuiPresetChromePalette = staticCompositionLocalOf<GuiPresetChromePalette> {
    error("Gui preset chrome requires ModernSettingsScaffold")
}

@Composable
internal fun rememberGuiPresetChromePalette(): GuiPresetChromePalette {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val canvas = colorResource(R.color.gui_preset_canvas)
    val canvasWithoutGlass = colorResource(R.color.gui_preset_canvas_glass_off)
    val cardFill = colorResource(R.color.gui_preset_card_fill)
    val pillFill = colorResource(R.color.gui_preset_pill_fill)
    val thumbFill = colorResource(R.color.gui_preset_thumb_fill)
    val dangerFill = colorResource(R.color.gui_preset_danger_fill)

    // Android selects drawable-night/ without a runtime per-cell color calculation.
    // One instance per shape is shared by the scaffold; drawing only changes bounds.
    val cardSpecular = remember(context, isDark) {
        requireNotNull(ContextCompat.getDrawable(context, R.drawable.gui_specular_card)).mutate()
    }
    val pillSpecular = remember(context, isDark) {
        requireNotNull(ContextCompat.getDrawable(context, R.drawable.gui_specular_pill)).mutate()
    }
    val thumbSpecular = remember(context, isDark) {
        requireNotNull(ContextCompat.getDrawable(context, R.drawable.gui_specular_thumb)).mutate()
    }
    return remember(
        canvas, canvasWithoutGlass, cardFill, pillFill, thumbFill, dangerFill,
        cardSpecular, pillSpecular, thumbSpecular,
    ) {
        GuiPresetChromePalette(
            canvas, canvasWithoutGlass, cardFill, pillFill, thumbFill, dangerFill,
            cardSpecular, pillSpecular, thumbSpecular,
        )
    }
}

internal enum class GuiSolidChromeStyle { CARD, PILL, THUMB, DANGER }

private val cardShape = RoundedCornerShape(24.dp)
private val pillShape = RoundedCornerShape(999.dp)

/**
 * Solid fill directly on the Cell, plus a transparent, baked 9-patch highlight.
 * The image contains white light concentrated at the top-left, with a soft
 * falloff along top/left and almost none on the bottom-right. 9-patch regions
 * preserve corner radius on differently sized cells without AGSL, shaders,
 * per-cell optical gradients or offscreen background capture.
 */
internal fun Modifier.guiSolidChrome(
    style: GuiSolidChromeStyle,
    palette: GuiPresetChromePalette,
): Modifier {
    val fill: Color
    val specular: Drawable
    when (style) {
        GuiSolidChromeStyle.CARD -> {
            fill = palette.cardFill
            specular = palette.cardSpecular
        }
        GuiSolidChromeStyle.PILL -> {
            fill = palette.pillFill
            specular = palette.pillSpecular
        }
        GuiSolidChromeStyle.THUMB -> {
            fill = palette.thumbFill
            specular = palette.thumbSpecular
        }
        GuiSolidChromeStyle.DANGER -> {
            fill = palette.dangerFill
            specular = palette.pillSpecular
        }
    }
    val shape = if (style == GuiSolidChromeStyle.CARD) cardShape else pillShape
    return this.background(fill, shape).guiDrawBakedSpecular(specular)
}

// The pressed slider and toggle thumbs keep Prismal's *actual* Ambient shader,
// whose layer alpha follows pressProgress. No baked overlay is added during touch.
private fun Modifier.guiDrawBakedSpecular(drawable: Drawable): Modifier =
    this.drawWithContent {
        drawContent()
        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            val width = size.width.roundToInt()
            val height = size.height.roundToInt()
            if (width > 0 && height > 0) {
                val save = nativeCanvas.save()
                try {
                    drawable.setBounds(0, 0, width, height)
                    drawable.draw(nativeCanvas)
                } finally {
                    nativeCanvas.restoreToCount(save)
                }
            }
        }
    }
