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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
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
    val rimContrast: Color,
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
    val cardColor = colorResource(R.color.gui_preset_card_fill).toArgb()
    val pillColor = colorResource(R.color.gui_preset_pill_fill).toArgb()
    val thumbColor = colorResource(R.color.gui_preset_thumb_fill).toArgb()
    val dangerColor = colorResource(R.color.gui_preset_danger_fill).toArgb()
    // Resource colors are shared. No Cell derives its own light direction.
    val rimContrast = colorResource(R.color.gui_preset_rim_contrast)
    val rimStrong = colorResource(R.color.gui_preset_rim_strong)
    val rimMedium = colorResource(R.color.gui_preset_rim_medium)
    val rimWeak = colorResource(R.color.gui_preset_rim_weak)

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
        rimContrast, rimStrong, rimMedium, rimWeak,
    ) {
        GuiPresetChromePalette(
            canvas, canvasWithoutGlass, cardMaterial, pillMaterial,
            thumbMaterial, dangerMaterial,
            rimContrast, rimStrong, rimMedium, rimWeak,
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


/**
 * Prismal's specular is a clipped, antialiased rounded-rectangle stroke.
 * The previous prebaked PNG lost its four corner glints when stretched and
 * was never valid for the 40x24dp XML thumb. Use the ACTUAL continuous
 * Prismal shape, cached once per measured size, and fixed resource colors.
 *
 * A plain outline is insufficient: sweep stops leave a white glint at ALL
 * four curved corners with stronger opposing 45-degree lobes, while the
 * dim contrast edge preserves the glossy transition on white handles.
 * This is cheap shape-stroke rendering, NOT a per-Cell AGSL, lens pass or blur.
 */
internal fun Modifier.guiStaticSpecularRim(
    shape: Shape,
    palette: GuiPresetChromePalette,
    compact: Boolean = false,
): Modifier = this.drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val colorStops = arrayOf(
        0f to palette.rimWeak,      // right side
        0.125f to palette.rimStrong, // bottom-right corner
        0.25f to palette.rimMedium, // bottom side
        0.375f to palette.rimMedium, // bottom-left corner
        0.5f to palette.rimWeak,   // left side
        0.625f to palette.rimStrong, // top-left corner
        0.75f to palette.rimMedium, // top side
        0.875f to palette.rimMedium, // top-right corner
        1f to palette.rimWeak,
    )
    val whiteReflection = Brush.sweepGradient(
        colorStops = colorStops,
        center = Offset(size.width / 2f, size.height / 2f),
    )
    val contrastWidth = if (compact) 2.2.dp.toPx() else 1.8.dp.toPx()
    val glintWidth = if (compact) 1.2.dp.toPx() else 1.15.dp.toPx()
    onDrawBehind {
        drawOutline(
            outline, color = palette.rimContrast,
            style = Stroke(width = contrastWidth),
        )
        drawOutline(
            outline, brush = whiteReflection,
            style = Stroke(width = glintWidth),
        )
    }
}

/**
 * Lightweight fixed soft edge in place of PrismalDepthShadow's per-item
 * Offscreen GraphicsLayer/RenderEffect. The shadow draws outside the clip.
 * Low-alpha concentric strokes approximate the native 24dp/4dp-vertical
 * shadow (or the handle's compact 4dp/0.67dp variant).
 *
 * This never loads a drawable, never allocates a GPU target, and therefore
 * cannot repeat the null-shadow resource startup crash.
 */
internal fun Modifier.guiStaticSoftShadow(
    shape: Shape,
    compact: Boolean = false,
): Modifier = this.drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val offsetY = if (compact) (4f / 6f).dp.toPx() else 4.dp.toPx()
    val layers = if (compact) {
        floatArrayOf(5.dp.toPx(), 3.dp.toPx(), 1.5.dp.toPx())
    } else {
        floatArrayOf(32.dp.toPx(), 18.dp.toPx(), 8.dp.toPx())
    }
    val opacities = if (compact) {
        floatArrayOf(0.012f, 0.016f, 0.018f)
    } else {
        floatArrayOf(0.008f, 0.016f, 0.025f)
    }
    onDrawBehind {
        translate(top = offsetY) {
            for (i in layers.indices) {
                drawOutline(
                    outline, color = Color.Black.copy(alpha = opacities[i]),
                    style = Stroke(width = layers[i]),
                )
            }
        }
    }
}
