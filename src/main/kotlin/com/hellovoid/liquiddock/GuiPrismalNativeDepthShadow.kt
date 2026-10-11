package com.hellovoid.liquiddock

import android.graphics.BlurMaskFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.styropyr0.prismal.depth.PrismalDepthShadow
import kotlin.math.ceil

/**
 * Isolated native Prismal DepthShadow renderer, without creating a glass
 * backdrop, refraction shader or sampling layer.
 *
 * The upstream PrismalAGSL v1.0.4 PrismalDepthShadowElement/Node and Paint.blur
 * are internal, so they cannot be referenced as a public library API. This
 * adaptation preserves their original rendering pipeline and shadow values:
 *
 *   offscreen GraphicsLayer -> BlurMaskFilter -> filled Shape outline ->
 *   BlendMode.Clear for the inner mask -> cached drawLayer() behind content.
 *
 * Adapted from styropyr0/PrismalAGSL v1.0.4 (MIT license),
 * Prismal/src/main/java/com/styropyr0/prismal/depth/PrismalDepthShadowModifier.kt
 * and Prismal/src/main/java/com/styropyr0/prismal/internal/Paint.kt.
 *
 * Unlike a full PrismalGlassSurface, this node does NOT sample the background.
 */
internal fun Modifier.guiNativePrismalDepthShadow(
    shape: Shape,
    shadow: PrismalDepthShadow = PrismalDepthShadow.Default,
): Modifier = this.then(GuiPrismalDepthShadowElement(shape, shadow))

private data class GuiPrismalDepthShadowElement(
    val shape: Shape,
    val shadow: PrismalDepthShadow,
) : ModifierNodeElement<GuiPrismalDepthShadowNode>() {
    override fun create(): GuiPrismalDepthShadowNode = GuiPrismalDepthShadowNode(shape, shadow)

    override fun update(node: GuiPrismalDepthShadowNode) {
        if (node.shape != shape || node.shadow != shadow) {
            node.shape = shape
            node.shadow = shadow
            node.invalidateDraw()
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "guiNativePrismalDepthShadow"
        properties["shape"] = shape
        properties["shadow"] = shadow
    }
}

private class GuiPrismalDepthShadowNode(
    var shape: Shape,
    var shadow: PrismalDepthShadow,
) : DrawModifierNode, Modifier.Node() {
    override val shouldAutoInvalidate: Boolean = false

    private var layer: GraphicsLayer? = null
    private val paint = Paint()
    private var blurRadius = Float.NaN
    private var recordedShadow: PrismalDepthShadow? = null
    private var cachedOutline: Outline? = null
    private var cachedShape: Shape? = null
    private var cachedSize = Size.Unspecified
    private var cachedDirection: LayoutDirection? = null
    private var cachedDensity = Float.NaN

    override fun onAttach() {
        layer = requireGraphicsContext().createGraphicsLayer().apply {
            compositingStrategy = CompositingStrategy.Offscreen
        }
    }

    override fun ContentDrawScope.draw() {
        val shadowLayer = layer ?: return drawContent()
        val currentSize = size
        val density: Density = this
        val direction = layoutDirection
        val radius = shadow.radius.toPx()

        // PrismalShapeLayout caches the outline. Reproduce this cache here:
        // comparing a newly constructed Outline.Generic by reference on every
        // frame would force an unnecessary blur re-record during scrolling.
        val needsOutline = cachedOutline == null ||
            cachedShape != shape || cachedSize != currentSize ||
            cachedDirection != direction || cachedDensity != density.density
        if (needsOutline) {
            cachedOutline = shape.createOutline(currentSize, direction, density)
            cachedShape = shape
            cachedSize = currentSize
            cachedDirection = direction
            cachedDensity = density.density
        }
        val outline = cachedOutline ?: return drawContent()

        shadowLayer.alpha = shadow.alpha
        shadowLayer.blendMode = shadow.blendMode
        val recordKey = shadow.copy(alpha = 1f, blendMode = DrawScope.DefaultBlendMode)
        if (needsOutline || recordKey != recordedShadow) {
            recordedShadow = recordKey
            val offsetX = shadow.offset.x.toPx()
            val offsetY = shadow.offset.y.toPx()
            val shadowSize = IntSize(
                ceil(currentSize.width + radius * 4f + offsetX).toInt(),
                ceil(currentSize.height + radius * 4f + offsetY).toInt(),
            )
            paint.color = shadow.color
            if (radius != blurRadius) {
                paint.asFrameworkPaint().maskFilter =
                    if (radius > 0f) BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
                    else null
                blurRadius = radius
            }
            shadowLayer.record(shadowSize) {
                translate(radius * 2f + offsetX, radius * 2f + offsetY) {
                    val canvas = drawContext.canvas
                    canvas.drawOutline(outline, paint)
                    canvas.translate(-offsetX, -offsetY)
                    canvas.drawOutline(outline, GuiPrismalClearMask)
                    canvas.translate(offsetX, offsetY)
                }
            }
        }

        translate(-radius * 2f, -radius * 2f) {
            drawLayer(shadowLayer)
        }
        drawContent()
    }

    override fun onDetach() {
        layer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        layer = null
        paint.asFrameworkPaint().maskFilter = null
        blurRadius = Float.NaN
        cachedOutline = null
        cachedShape = null
        cachedSize = Size.Unspecified
        cachedDirection = null
        cachedDensity = Float.NaN
        recordedShadow = null
    }
}

private val GuiPrismalClearMask = Paint().apply {
    blendMode = BlendMode.Clear
}
