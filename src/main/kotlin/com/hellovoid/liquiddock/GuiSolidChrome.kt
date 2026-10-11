package com.hellovoid.liquiddock

import android.graphics.drawable.Drawable
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
    val cardShadow: Drawable,
    val pillShadow: Drawable,
)

internal val LocalGuiPresetChromePalette = staticCompositionLocalOf<GuiPresetChromePalette> {
    error("GUI preset material requires ModernSettingsScaffold")
}

@Composable
internal fun rememberGuiPresetChromePalette(): GuiPresetChromePalette {
    val context = LocalContext.current
    val canvas = colorResource(R.color.gui_preset_canvas)
    val canvasWithoutGlass = colorResource(R.color.gui_preset_canvas_glass_off)

    // Android night/nodpi qualifiers select the correct source. Load four
    // drawable instances once per scaffold and reuse the same baked pixels.
    val cardMaterial = remember(context, canvas) {
        requireNotNull(ContextCompat.getDrawable(context, R.drawable.gui_material_card)).mutate()
    }
    val pillMaterial = remember(context, canvas) {
        requireNotNull(ContextCompat.getDrawable(context, R.drawable.gui_material_pill)).mutate()
    }
    val thumbMaterial = remember(context, canvas) {
        requireNotNull(ContextCompat.getDrawable(context, R.drawable.gui_material_thumb)).mutate()
    }
    val dangerMaterial = remember(context, canvas) {
        requireNotNull(ContextCompat.getDrawable(context, R.drawable.gui_material_danger)).mutate()
    }
    // This is a pre-blurred 24dp, 4dp-Y-offset approximation of
    // PrismalDepthShadow.Default (black, 10%). Only two shared images.
    val cardShadow = remember(context) {
        requireNotNull(ContextCompat.getDrawable(context, R.drawable.gui_depth_shadow_card)).mutate()
    }
    val pillShadow = remember(context) {
        requireNotNull(ContextCompat.getDrawable(context, R.drawable.gui_depth_shadow_pill)).mutate()
    }
    return remember(
        canvas, canvasWithoutGlass, cardMaterial, pillMaterial,
        thumbMaterial, dangerMaterial, cardShadow, pillShadow,
    ) {
        GuiPresetChromePalette(
            canvas, canvasWithoutGlass, cardMaterial, pillMaterial,
            thumbMaterial, dangerMaterial, cardShadow, pillShadow,
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
            // Android XML <shape> drawables have no intrinsic dimensions.
            // Previously this path returned an empty draw and completely hid
            // the idle slider/toggle thumb and destructive actions.
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
 * PrismalDepthShadow.Default: 24dp soft shadow, 4dp downward offset,
 * black at alpha 0.10. Cached as an off-line blurred RGBA mask so a Cell
 * doesn't instantiate PrismalDepthShadowNode/GraphicsLayer per row.
 *
 * This modifier MUST be placed BEFORE clip(shape), otherwise the real
 * exterior halo and the rounded corner falloff get clipped away.
 */
internal fun Modifier.guiBakedDepthShadow(
    style: GuiSolidChromeStyle,
    palette: GuiPresetChromePalette,
): Modifier {
    val drawable = when (style) {
        GuiSolidChromeStyle.CARD -> palette.cardShadow
        GuiSolidChromeStyle.PILL, GuiSolidChromeStyle.DANGER -> palette.pillShadow
        GuiSolidChromeStyle.THUMB -> return this
    }
    val cornerDp = if (style == GuiSolidChromeStyle.CARD) 24f else 18f
    return this.drawWithCache {
        val width = size.width.roundToInt()
        val height = size.height.roundToInt()
        val sw = drawable.intrinsicWidth
        val sh = drawable.intrinsicHeight
        if (width <= 0 || height <= 0 || sw <= 0 || sh <= 0) {
            onDrawBehind {}
        } else {
            // Source includes a 24dp transparent halo outside the fixed shape.
            // Nine-slice the halo *with* its corner radii to preserve blur.
            val padPx = 24f * density
            val corner = (cornerDp + 24f) * density
            val ox = (cornerDp + 24f) * 3f // assets baked at 3px/dp
            val sourceX = intArrayOf(0, ox.roundToInt(), sw - ox.roundToInt(), sw)
            val sourceY = intArrayOf(0, ox.roundToInt(), sh - ox.roundToInt(), sh)
            val dx = floatArrayOf(
                -padPx,
                (-padPx + corner).coerceAtMost(width * .5f),
                (width + padPx - corner).coerceAtLeast(width * .5f),
                width + padPx,
            )
            val dy = floatArrayOf(
                -padPx,
                (-padPx + corner).coerceAtMost(height * .5f),
                (height + padPx - corner).coerceAtLeast(height * .5f),
                height + padPx,
            )
            onDrawBehind {
                drawIntoCanvas { canvas ->
                    drawBakedNineSlice(
                        native = canvas.nativeCanvas,
                        material = drawable,
                        sourceWidth = sw,
                        sourceHeight = sh,
                        sx = sourceX, sy = sourceY, dx = dx, dy = dy,
                    )
                }
            }
        }
    }
}
