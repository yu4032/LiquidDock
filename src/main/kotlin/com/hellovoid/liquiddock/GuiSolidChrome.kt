package com.hellovoid.liquiddock

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/**
 * The GUI backdrop is fixed; bake the FINAL composite of the background,
 * saturated Plus-blend Prismal Default specular, and shape edge into a reusable fixed raster resource.
 *
 * The source default_specular.agsl yields a normal-dependent WHITE source,
 * but PrismalSpecularModifier draws that source with BlendMode.Plus and clips
 * it to a continuous rounded rectangle. A plain white SRC_OVER bitmap cannot
 * reproduce the result over our near-white palette: Plus saturates thin lines
 * all the way to white. The static PNG therefore stores the actual composited
 * opaque RGB result, not a standalone alpha map or a fake white outline.
 *
 * Upstream: PrismalAGSL v1.0.4 PrismalSpecularModifier.kt and
 * default_specular.agsl. Assets built offline from the fixed light/dark canvas.
 */
internal data class GuiPresetChromePalette(
    val canvas: Color,
    val canvasWithoutGlass: Color,
    val cardMaterial: Drawable,
    val pillMaterial: Drawable,
    val thumbMaterial: Drawable,
    val dangerMaterial: Drawable,
)

internal val LocalGuiPresetChromePalette = staticCompositionLocalOf<GuiPresetChromePalette> {
    error("GUI preset material requires ModernSettingsScaffold")
}

@Composable
internal fun rememberGuiPresetChromePalette(): GuiPresetChromePalette {
    val context = LocalContext.current
    val canvas = colorResource(R.color.gui_preset_canvas)
    val canvasWithoutGlass = colorResource(R.color.gui_preset_canvas_glass_off)
    val cardColor = colorResource(R.color.gui_preset_card_fill).toArgb()
    val pillColor = colorResource(R.color.gui_preset_pill_fill).toArgb()
    val thumbColor = colorResource(R.color.gui_preset_thumb_fill).toArgb()
    val dangerColor = colorResource(R.color.gui_preset_danger_fill).toArgb()

    // Android night/nodpi qualifiers select the correct source. Load four
    // drawable instances once per scaffold and reuse the same baked pixels.
    val cardMaterial = remember(context, cardColor) {
        loadGuiChromeOrSolidFallback(
            context, R.drawable.gui_material_card, cardColor, 24f,
            "gui_material_card",
        )
    }
    val pillMaterial = remember(context, pillColor) {
        loadGuiChromeOrSolidFallback(
            context, R.drawable.gui_material_pill, pillColor, 18f,
            "gui_material_pill",
        )
    }
    val thumbMaterial = remember(context, thumbColor) {
        loadGuiChromeOrSolidFallback(
            context, R.drawable.gui_material_thumb, thumbColor, 999f,
            "gui_material_thumb",
        )
    }
    val dangerMaterial = remember(context, dangerColor) {
        loadGuiChromeOrSolidFallback(
            context, R.drawable.gui_material_danger, dangerColor, 999f,
            "gui_material_danger",
        )
    }
    return remember(
        canvas, canvasWithoutGlass, cardMaterial, pillMaterial,
        thumbMaterial, dangerMaterial,
    ) {
        GuiPresetChromePalette(
            canvas, canvasWithoutGlass, cardMaterial, pillMaterial,
            thumbMaterial, dangerMaterial,
        )
    }
}

internal enum class GuiSolidChromeStyle { CARD, PILL, THUMB, DANGER }

/**
 * Draw the precomposited RGB underneath text/icons (never a foreground white
 * stroke that can bleach UI content). Android AAPT2 9-patch compilers crashed
 * with the baked assets, so we deliberately use STANDARD PNGs and draw a
 * deterministic nine-region stretch at runtime. Corners remain at their
 * physical dp radii; only straight middle bands stretch.
 *
 * This is up to nine cheap clipped draws of one shared image. The nine-slice
 * geometry is cached by drawWithCache when size/density changes, so scrolling
 * does not allocate arrays per frame. There is no per-Cell Bitmap allocation,
 * RenderEffect, shader, capture, offscreen layer, lighting or background child.
 */
internal fun Modifier.guiSolidChrome(
    style: GuiSolidChromeStyle,
    palette: GuiPresetChromePalette,
): Modifier {
    val material = when (style) {
        GuiSolidChromeStyle.CARD -> palette.cardMaterial
        GuiSolidChromeStyle.PILL -> palette.pillMaterial
        GuiSolidChromeStyle.THUMB -> palette.thumbMaterial
        GuiSolidChromeStyle.DANGER -> palette.dangerMaterial
    }
    val cornerDp = when (style) {
        GuiSolidChromeStyle.CARD -> 24f
        GuiSolidChromeStyle.PILL -> 18f
        GuiSolidChromeStyle.THUMB, GuiSolidChromeStyle.DANGER -> 0f
    }
    // IMPORTANT: Build source/destination slice geometry once per measured
    // Cell size. Scrolling must not allocate four arrays for every draw/frame.
    return this.drawWithCache {
        val width = size.width.roundToInt()
        val height = size.height.roundToInt()
        val sourceWidth = material.intrinsicWidth
        val sourceHeight = material.intrinsicHeight
        if (width <= 0 || height <= 0) {
            onDrawBehind {}
        } else if (cornerDp == 0f || sourceWidth <= 0 || sourceHeight <= 0) {
            // XML ShapeDrawable reports negative intrinsic dimensions:
            // draw at its measured destination size instead of hiding it.
            onDrawBehind {
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    val checkpoint = native.save()
                    try {
                        material.setBounds(0, 0, width, height)
                        material.draw(native)
                    } finally {
                        native.restoreToCount(checkpoint)
                    }
                }
            }
        } else {
            val sourceCornerPx = (cornerDp * 3f).roundToInt()
            val sx = intArrayOf(
                1, 1 + sourceCornerPx,
                sourceWidth - 1 - sourceCornerPx, sourceWidth - 1,
            )
            val sy = intArrayOf(
                1, 1 + sourceCornerPx,
                sourceHeight - 1 - sourceCornerPx, sourceHeight - 1,
            )
            val cornerX = (cornerDp * density).coerceAtMost(width * 0.5f)
            val cornerY = (cornerDp * density).coerceAtMost(height * 0.5f)
            val dx = floatArrayOf(0f, cornerX, width - cornerX, width.toFloat())
            val dy = floatArrayOf(0f, cornerY, height - cornerY, height.toFloat())
            onDrawBehind {
                drawIntoCanvas { canvas ->
                    drawBakedNineSlice(
                        native = canvas.nativeCanvas,
                        material = material,
                        sourceWidth = sourceWidth,
                        sourceHeight = sourceHeight,
                        sx = sx, sy = sy, dx = dx, dy = dy,
                    )
                }
            }
        }
    }
}

private fun drawBakedNineSlice(
    native: android.graphics.Canvas,
    material: Drawable,
    sourceWidth: Int,
    sourceHeight: Int,
    sx: IntArray,
    sy: IntArray,
    dx: FloatArray,
    dy: FloatArray,
) {
    for (row in 0..2) {
        for (column in 0..2) {
            val srcWidth = sx[column + 1] - sx[column]
            val srcHeight = sy[row + 1] - sy[row]
            val dstWidth = dx[column + 1] - dx[column]
            val dstHeight = dy[row + 1] - dy[row]
            if (srcWidth <= 0 || srcHeight <= 0 || dstWidth <= 0f || dstHeight <= 0f) continue

            val saved = native.save()
            try {
                native.clipRect(
                    dx[column], dy[row],
                    dx[column + 1], dy[row + 1],
                )
                native.translate(dx[column], dy[row])
                native.scale(dstWidth / srcWidth, dstHeight / srcHeight)
                material.setBounds(
                    -sx[column], -sy[row],
                    sourceWidth - sx[column], sourceHeight - sy[row],
                )
                material.draw(native)
            } finally {
                native.restoreToCount(saved)
            }
        }
    }
}

/**
 * Optional decorative resources must never prevent the settings Activity from
 * starting. Several device ROMs returned null for dynamically loaded baked
 * PNG shadows; requireNotNull() crashed during the first composition.
 */
private fun loadGuiChromeOrSolidFallback(
    context: Context,
    resourceId: Int,
    fallbackColor: Int,
    radiusDp: Float,
    label: String,
): Drawable {
    try {
        val candidate = ContextCompat.getDrawable(context, resourceId)
        if (candidate != null) return candidate.mutate()
        Log.w("GuiSolidChrome", "$label returned null, using a solid chrome fallback")
    } catch (error: RuntimeException) {
        Log.w("GuiSolidChrome", "Failed to load $label; using solid chrome", error)
    }
    val density = context.resources.displayMetrics.density
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusDp * density
        setColor(fallbackColor)
        setStroke((density).roundToInt().coerceAtLeast(1), 0x55FFFFFF)
    }
}
