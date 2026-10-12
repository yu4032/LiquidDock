package com.hellovoid.liquiddock

import android.graphics.BlurMaskFilter
import android.os.Build
import android.util.Log
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.styropyr0.prismal.PrismalShader
import com.styropyr0.prismal.asPrismalShaderHost
import com.styropyr0.prismal.shapes.PrismalRoundedRectangularShape
import kotlin.math.ceil
import kotlin.math.min

/**
 * Button-only Prismal v1.0.4 specular renderer, independent of glass capture.
 *
 * The previous GUI rim used a center-based sweep gradient. On wide/short
 * buttons the sweep changes apparent stroke thickness across long straight
 * sides. Upstream instead paints ONE uniform-width shape stroke, clips it to
 * the exact continuous outline, applies a normal-based 45-degree AGSL light,
 * and blends the resulting layer with Plus.
 *
 * Adapted from PrismalSpecularModifier.kt and upstream Default AGSL (MIT).
 * Restricted to actions/stepper controls, not the many static settings Cells.
 * All geometry, blur and shader uniforms are recorded only on size/shape/
 * density changes; ordinary scrolling only draws the cached GraphicsLayer.
 */
internal fun Modifier.guiNativePrismalButtonSpecular(shape: Shape): Modifier =
    this.then(GuiButtonSpecularElement(shape))

private data class GuiButtonSpecularElement(val shape: Shape) :
    ModifierNodeElement<GuiButtonSpecularNode>() {
    override fun create() = GuiButtonSpecularNode(shape)

    override fun update(node: GuiButtonSpecularNode) {
        if (node.shape != shape) {
            node.shape = shape
            node.invalidateDraw()
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "guiNativePrismalButtonSpecular"
        properties["shape"] = shape
    }
}

private class GuiButtonSpecularNode(var shape: Shape) : DrawModifierNode, Modifier.Node() {
    override val shouldAutoInvalidate = false

    private var layer: GraphicsLayer? = null
    private val paint = Paint().apply {
        style = PaintingStyle.Stroke
        color = Color.White.copy(alpha = 0.5f) // PrismalSpecularStyle.Default
    }
    private var cachedOutline: Outline? = null
    private var cachedShape: Shape? = null
    private var cachedSize = Size.Unspecified
    private var cachedDirection: LayoutDirection? = null
    private var cachedDensity = Float.NaN
    private var cachedBlur = Float.NaN
    private var shader: PrismalShader? = null
    private var shaderUnavailable = false
    private var roundedClipPath: Path? = null

    override fun onAttach() {
        layer = requireGraphicsContext().createGraphicsLayer()
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val specularLayer = layer ?: return
        val sizeNow = size
        if (sizeNow.width <= 0f || sizeNow.height <= 0f) return

        val needsRecord = cachedOutline == null ||
            cachedShape != shape || cachedSize != sizeNow ||
            cachedDirection != layoutDirection || cachedDensity != density
        if (needsRecord) {
            cachedOutline = shape.createOutline(sizeNow, layoutDirection, this)
            cachedShape = shape
            cachedSize = sizeNow
            cachedDirection = layoutDirection
            cachedDensity = density
            val outline = cachedOutline ?: return
            if (outline is Outline.Rounded && roundedClipPath == null) roundedClipPath = Path()

            // Identical source geometry to PrismalSpecularModifier.configurePaint.
            // Clipping removes the outer half: inner apparent width is always
            // ceil(0.5dp * density), even at the four continuous-curve corners.
            val widthPx = min(0.5f.dp.toPx(), sizeNow.minDimension / 2f)
            paint.strokeWidth = ceil(widthPx.toDouble()).toFloat() * 2f
            val blurPx = 0.25f.dp.toPx()
            if (blurPx != cachedBlur) {
                paint.asFrameworkPaint().maskFilter =
                    if (blurPx > 0f) BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL) else null
                cachedBlur = blurPx
            }
            configureSourceShader(sizeNow, layoutDirection, this)

            specularLayer.record(
                IntSize(ceil(sizeNow.width.toDouble()).toInt() + 2,
                    ceil(sizeNow.height.toDouble()).toInt() + 2),
            ) {
                translate(1f, 1f) {
                    val canvas = drawContext.canvas
                    canvas.save()
                    try {
                        canvas.clipGuiSpecularOutline(outline, roundedClipPath)
                        canvas.drawOutline(outline, paint)
                    } finally {
                        canvas.restore()
                    }
                }
            }
        }

        specularLayer.alpha = 1f
        specularLayer.blendMode = BlendMode.Plus
        translate(-1f, -1f) { drawLayer(specularLayer) }
    }

    private fun ContentDrawScope.configureSourceShader(
        dimensions: Size,
        direction: LayoutDirection,
        densityScope: Density,
    ) {
        // The original Default style uses a platform AGSL shader on API 33+.
        // Fall back to its plain white stroke if a ROM cannot compile AGSL;
        // decorative failure must not crash the settings Activity.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || shaderUnavailable) {
            paint.asFrameworkPaint().shader = null
            return
        }
        try {
            val currentShader = shader ?: PrismalShader(GUI_BUTTON_PRISMAL_DEFAULT_AGSL)
                .also { this@GuiButtonSpecularNode.shader = it }
            val radius = if (shape is PrismalRoundedRectangularShape) {
                (shape as PrismalRoundedRectangularShape).corners(dimensions, direction, densityScope)
                    .let { floatArrayOf(it.topLeft, it.topRight, it.bottomRight, it.bottomLeft) }
            } else {
                FloatArray(4) { dimensions.minDimension / 2f }
            }
            currentShader.setFloatUniform("size", dimensions.width, dimensions.height)
            currentShader.setFloatUniform("cornerRadii", radius)
            currentShader.setColorUniform("color", Color.White)
            currentShader.setFloatUniform("angle", 0.7853981634f) // 45° in radians
            currentShader.setFloatUniform("falloff", 1f)
            paint.asFrameworkPaint().shader = currentShader.asPrismalShaderHost()
        } catch (error: RuntimeException) {
            Log.w("GuiPrismalSpecular", "Native specular shader unavailable; using plain rim", error)
            shaderUnavailable = true
            paint.asFrameworkPaint().shader = null
        }
    }

    override fun onDetach() {
        layer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        layer = null
        paint.asFrameworkPaint().shader = null
        paint.asFrameworkPaint().maskFilter = null
        shader = null
        shaderUnavailable = false
        roundedClipPath = null
        cachedOutline = null
        cachedShape = null
        cachedSize = Size.Unspecified
        cachedDirection = null
        cachedDensity = Float.NaN
        cachedBlur = Float.NaN
    }
}

private fun Canvas.clipGuiSpecularOutline(outline: Outline, reusable: Path?) {
    when (outline) {
        is Outline.Rectangle -> clipRect(outline.rect)
        is Outline.Generic -> clipPath(outline.path)
        is Outline.Rounded -> {
            val path = reusable ?: Path()
            path.rewind()
            path.addRoundRect(outline.roundRect)
            clipPath(path)
        }
    }
}

/**
 * Original PrismalAGSL v1.0.4 rounded_rect_sdf.agsl +
 * default_specular.agsl, used only by the button render node above.
 */
private const val GUI_BUTTON_PRISMAL_DEFAULT_AGSL = """
float prismalCornerRadiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}
float2 prismalGradSdRoundRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}
uniform float2 size;
uniform float4 cornerRadii;
layout(color) uniform half4 color;
uniform float angle;
uniform float falloff;
half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = prismalCornerRadiusAt(coord, cornerRadii);
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = prismalGradSdRoundRect(centeredCoord, halfSize, gradRadius);
    float2 normal = float2(cos(angle), sin(angle));
    float d = dot(grad, normal);
    float intensity = pow(abs(d), falloff);
    return color * intensity;
}
"""
