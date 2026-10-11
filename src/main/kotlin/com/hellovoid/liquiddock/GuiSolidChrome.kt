package com.hellovoid.liquiddock

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
 * This is a few ordinary draw calls using a single shared image resource.
 * It creates no per-Cell Bitmap, RenderEffect, Prismal shader, capture, offscreen
 * layer, per-Cell color calculation or extra composable background child.
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
    return this.drawBehind {
        drawIntoCanvas { canvas ->
            val width = size.width.roundToInt()
            val height = size.height.roundToInt()
            if (width <= 0 || height <= 0) return@drawIntoCanvas
            val native = canvas.nativeCanvas
            if (cornerDp == 0f) {
                // Plain Android XML drawable: a single cheap white/red shape.
                val saved = native.save()
                try {
                    material.setBounds(0, 0, width, height)
                    material.draw(native)
                } finally {
                    native.restoreToCount(saved)
                }
            } else {
                // Baked assets are stored as drawable[-night]-nodpi normal PNGs.
                // Their 1px guide frame is cropped here; AAPT2 never sees a
                // .9.png and cannot crash when compiling its custom metadata.
                drawBakedNineSlice(
                    native = native,
                    material = material,
                    width = width,
                    height = height,
                    sourceCornerPx = (cornerDp * 3f).roundToInt(), // xxhdpi baker
                    targetCornerPx = cornerDp * density,
                )
            }
        }
    }
}

private fun drawBakedNineSlice(
    native: android.graphics.Canvas,
    material: Drawable,
    width: Int,
    height: Int,
    sourceCornerPx: Int,
    targetCornerPx: Float,
) {
    val sourceWidth = material.intrinsicWidth
    val sourceHeight = material.intrinsicHeight
    if (sourceWidth <= 2 || sourceHeight <= 2) return

    val sx = intArrayOf(
        1,
        1 + sourceCornerPx,
        sourceWidth - 1 - sourceCornerPx,
        sourceWidth - 1,
    )
    val sy = intArrayOf(
        1,
        1 + sourceCornerPx,
        sourceHeight - 1 - sourceCornerPx,
        sourceHeight - 1,
    )
    val cx = targetCornerPx.coerceAtMost(width * 0.5f)
    val cy = targetCornerPx.coerceAtMost(height * 0.5f)
    val dx = floatArrayOf(0f, cx, width - cx, width.toFloat())
    val dy = floatArrayOf(0f, cy, height - cy, height.toFloat())

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
