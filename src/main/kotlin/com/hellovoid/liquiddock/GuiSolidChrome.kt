package com.hellovoid.liquiddock

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/**
 * Static setting Cells use fixed palette fills and one highlight along the
 * actual Prismal outline. Only the idle thumb uses an Android drawable.
 * This preserves the performance gain without retaining abandoned baked
 * Card/Pill PNGs, nine-slice rendering or per-Cell refraction.
 */
internal data class GuiPresetChromePalette(
    val canvas: Color,
    val canvasWithoutGlass: Color,
    val cardFill: Color,
    val pillFill: Color,
    val thumbFill: Color,
    val dangerFill: Color,
    val thumbDrawable: Drawable,
    val rimStrong: Color,
    val rimMedium: Color,
    val rimWeak: Color,
)

internal val LocalGuiPresetChromePalette = staticCompositionLocalOf<GuiPresetChromePalette> {
    error("GUI preset material requires ModernSettingsScaffold")
}

@Composable
internal fun rememberGuiPresetChromePalette(): GuiPresetChromePalette {
    val context = LocalContext.current
    val canvas = colorResource(R.color.gui_preset_canvas)
    val canvasWithoutGlass = colorResource(R.color.gui_preset_canvas_glass_off)
    val cardFill = colorResource(R.color.gui_preset_card_fill)
    val pillFill = colorResource(R.color.gui_preset_pill_fill)
    val thumbFill = colorResource(R.color.gui_preset_thumb_fill)
    val dangerFill = colorResource(R.color.gui_preset_danger_fill)
    val rimStrong = colorResource(R.color.gui_preset_rim_strong)
    val rimMedium = colorResource(R.color.gui_preset_rim_medium)
    val rimWeak = colorResource(R.color.gui_preset_rim_weak)

    // Decor is optional: a malformed or missing drawable cannot crash settings.
    val thumbDrawable = remember(context, thumbFill) {
        loadGuiThumbOrSolidFallback(context, R.drawable.gui_material_thumb, thumbFill)
    }
    return remember(
        canvas, canvasWithoutGlass, cardFill, pillFill, thumbFill, dangerFill,
        thumbDrawable, rimStrong, rimMedium, rimWeak,
    ) {
        GuiPresetChromePalette(
            canvas, canvasWithoutGlass, cardFill, pillFill, thumbFill, dangerFill,
            thumbDrawable, rimStrong, rimMedium, rimWeak,
        )
    }
}

internal enum class GuiSolidChromeStyle { CARD, PILL, THUMB, DANGER }

/** One cached fill and one rounded-specular stroke; no duplicate outlines. */
internal fun Modifier.guiExactPrismalCornerChrome(
    style: GuiSolidChromeStyle,
    palette: GuiPresetChromePalette,
    shape: Shape,
): Modifier {
    val fill = when (style) {
        GuiSolidChromeStyle.CARD -> palette.cardFill
        GuiSolidChromeStyle.PILL -> palette.pillFill
        GuiSolidChromeStyle.THUMB -> palette.thumbFill
        GuiSolidChromeStyle.DANGER -> palette.dangerFill
    }
    return this.background(fill, shape).guiStaticSpecularRim(shape, palette)
}

/** XML ShapeDrawable has no intrinsic size; always draw at measured bounds. */
internal fun Modifier.guiIdleThumbChrome(palette: GuiPresetChromePalette): Modifier =
    this.drawWithCache {
        val width = size.width.roundToInt()
        val height = size.height.roundToInt()
        onDrawBehind {
            if (width > 0 && height > 0) {
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    val checkpoint = native.save()
                    try {
                        palette.thumbDrawable.setBounds(0, 0, width, height)
                        palette.thumbDrawable.draw(native)
                    } finally {
                        native.restoreToCount(checkpoint)
                    }
                }
            }
        }
    }

/** Preserve the verified startup-safe fallback for optional thumb chrome. */
private fun loadGuiThumbOrSolidFallback(
    context: Context,
    resourceId: Int,
    fallbackColor: Color,
): Drawable {
    try {
        val loaded = ContextCompat.getDrawable(context, resourceId)
        if (loaded != null) return loaded.mutate()
        Log.w("GuiSolidChrome", "Idle thumb drawable returned null; using solid fallback")
    } catch (error: RuntimeException) {
        Log.w("GuiSolidChrome", "Idle thumb drawable failed; using solid fallback", error)
    }
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 999f * context.resources.displayMetrics.density
        setColor(android.graphics.Color.argb(
            (fallbackColor.alpha * 255).roundToInt(),
            (fallbackColor.red * 255).roundToInt(),
            (fallbackColor.green * 255).roundToInt(),
            (fallbackColor.blue * 255).roundToInt(),
        ))
    }
}

/** One cached sweep reflection following Prismal's continuous shape. */
internal fun Modifier.guiStaticSpecularRim(
    shape: Shape,
    palette: GuiPresetChromePalette,
    compact: Boolean = false,
): Modifier = this.drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val whiteReflection = Brush.sweepGradient(
        0f to palette.rimWeak,       // right side
        0.125f to palette.rimStrong, // bottom-right corner
        0.25f to palette.rimMedium,  // bottom side
        0.375f to palette.rimMedium, // bottom-left corner
        0.5f to palette.rimWeak,     // left side
        0.625f to palette.rimStrong, // top-left corner
        0.75f to palette.rimMedium,  // top side
        0.875f to palette.rimMedium, // top-right corner
        1f to palette.rimWeak,
        center = Offset(size.width / 2f, size.height / 2f),
    )
    val glintWidth = if (compact) 0.8.dp.toPx() else 1.dp.toPx()
    onDrawBehind {
        drawOutline(
            outline, brush = whiteReflection,
            style = Stroke(width = glintWidth),
        )
    }
}
