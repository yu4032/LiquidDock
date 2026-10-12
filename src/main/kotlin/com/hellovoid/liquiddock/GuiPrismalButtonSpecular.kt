package com.hellovoid.liquiddock

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawImage
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Fully offline Prismal button highlighter. The PNG atlas is created by
 * scripts/generate_gui_button_specular.mjs and decoded ONCE in the GUI scaffold.
 * No AGSL shader, GraphicsLayer, RenderEffect, Paint, BlurMaskFilter, or
 * calculation of light/normals at runtime.
 *
 * Rows contain 36dp-high images with exact source continuous corners for
 * every 1dp width from 36 to 72; 3 source pixels per dp. At >=72dp the
 * upstream corner curve is stable, so stretch only the 4dp CENTRAL straight
 * portion of the widest atlas entry. Corner/rim pixels are never stretched.
 *
 * The transparent-white baked intensity is composited with Plus, just like
 * PrismalSpecularStyle.Default. Background colors remain theme-specific.
 */
internal fun Modifier.guiBakedPrismalButtonSpecular(
    atlas: ImageBitmap?,
): Modifier {
    if (atlas == null) return this
    return this.drawWithCache {
        val drawWidth = size.width.roundToInt()
        val drawHeight = size.height.roundToInt()
        val roundedDp = (size.width / density).roundToInt().coerceIn(MIN_DP, MAX_DP)
        val rowY = (roundedDp - MIN_DP) * ROW_PIXELS
        val sourceWidth = roundedDp * SCALE
        val drawSliced = (size.width / density) > MAX_DP && drawWidth > 0
        val sideDst = (SIDE_DP * density).roundToInt()
        onDrawBehind {
            if (drawWidth <= 0 || drawHeight <= 0) return@onDrawBehind
            if (!drawSliced || drawWidth <= 2 * sideDst) {
                drawImage(
                    image = atlas,
                    srcOffset = IntOffset(0, rowY),
                    srcSize = IntSize(sourceWidth, ROW_PIXELS),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(drawWidth, drawHeight),
                    filterQuality = FilterQuality.Medium,
                    blendMode = BlendMode.Plus,
                )
            } else {
                val middleWidth = drawWidth - 2 * sideDst
                // Three fragments; only the 4dp straight middle stretches.
                drawImage(
                    image = atlas,
                    srcOffset = IntOffset(0, rowY),
                    srcSize = IntSize(SIDE_DP * SCALE, ROW_PIXELS),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(sideDst, drawHeight),
                    filterQuality = FilterQuality.Medium,
                    blendMode = BlendMode.Plus,
                )
                drawImage(
                    image = atlas,
                    srcOffset = IntOffset(SIDE_DP * SCALE, rowY),
                    srcSize = IntSize((MAX_DP - 2 * SIDE_DP) * SCALE, ROW_PIXELS),
                    dstOffset = IntOffset(sideDst, 0),
                    dstSize = IntSize(middleWidth, drawHeight),
                    filterQuality = FilterQuality.Medium,
                    blendMode = BlendMode.Plus,
                )
                drawImage(
                    image = atlas,
                    srcOffset = IntOffset((MAX_DP - SIDE_DP) * SCALE, rowY),
                    srcSize = IntSize(SIDE_DP * SCALE, ROW_PIXELS),
                    dstOffset = IntOffset(drawWidth - sideDst, 0),
                    dstSize = IntSize(sideDst, drawHeight),
                    filterQuality = FilterQuality.Medium,
                    blendMode = BlendMode.Plus,
                )
            }
        }
    }
}

private const val MIN_DP = 36
private const val MAX_DP = 72
private const val SIDE_DP = 34
private const val SCALE = 3
private const val ROW_PIXELS = 36 * SCALE
