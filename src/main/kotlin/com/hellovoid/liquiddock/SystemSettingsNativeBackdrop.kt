package com.hellovoid.liquiddock

import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.RenderNode
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import com.styropyr0.prismal.PrismalBackdrop
import kotlin.math.max
import kotlin.math.min

/**
 * Records the actual native Settings hierarchy as GPU display-list commands
 * in an Android RenderNode. No Bitmap, PixelCopy, ScreenCapture, readPixels or
 * software readback. Capture is bounded to the switch regions plus glass blur
 * overscan. The host's injected ComposeViews skip their draw during recording,
 * so the glass can never capture itself (feedback loop).
 *
 * RenderNode is an image source only on a hardware accelerated Canvas. If the
 * native hierarchy cannot be recorded safely, no scene sample is published.
 */
internal class SystemSettingsNativeBackdrop(
    private val root: FrameLayout,
) : PrismalBackdrop {
    private val node = RenderNode("LiquidDock-Settings-Prismal-Backdrop")
    private val visibleSwitches = LinkedHashMap<View, View>()
    private val rootPosition = IntArray(2)
    private val targetPosition = IntArray(2)
    private val clip = RectF()
    private var sampleVersion by mutableIntStateOf(0)
    private var lastCaptureTime = 0L
    private var dirty = true
    private var disabled = false
    var capturing = false
        private set

    override val isCoordinatesDependent = true

    override fun readSamplingState() {
        // A recorded display list is a mutable GPU source. Its version is
        // observable by Compose, so Prismal rerenders after a scene refresh.
        sampleVersion
    }

    fun add(stock: View, visual: View) {
        visibleSwitches[stock] = visual
        requestCapture()
    }

    fun remove(stock: View) {
        visibleSwitches.remove(stock)
        requestCapture()
    }

    fun isEmpty(): Boolean = visibleSwitches.isEmpty()

    fun requestCapture() {
        dirty = true
        if (root.isAttachedToWindow) root.postInvalidateOnAnimation()
    }

    /** Invoked only from the root pre-draw observer on the UI thread. */
    fun refreshIfNeeded(): Boolean {
        if (!dirty || disabled || !root.isAttachedToWindow || capturing) return false
        val now = SystemClock.uptimeMillis()
        if (lastCaptureTime != 0L && now - lastCaptureTime < 25L) return false
        val width = root.width
        val height = root.height
        if (width <= 0 || height <= 0) return false
        if (!calculateClip(width, height)) return false
        // Defer any glass-window drawing until this recording has ended.
        dirty = false
        capturing = true
        var recording = false
        try {
            node.setPosition(0, 0, width, height)
            val canvas = node.beginRecording(width, height)
            recording = true
            val count = canvas.save()
            canvas.clipRect(clip)
            root.draw(canvas)
            canvas.restoreToCount(count)
            node.endRecording()
            recording = false
            sampleVersion++
            lastCaptureTime = now
            return true
        } catch (error: Throwable) {
            disabled = true
            Api101Bridge.log("[DC][SettingsSwitch] native GPU backdrop disabled " +
                error.javaClass.name + ": " + error.message)
            if (recording) {
                try { node.endRecording() } catch (_: Throwable) {}
            }
            return false
        } finally {
            capturing = false
        }
    }

    private fun calculateClip(width: Int, height: Int): Boolean {
        root.getLocationInWindow(rootPosition)
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        val overscan = 28f * root.resources.displayMetrics.density
        for ((stock, visual) in visibleSwitches) {
            if (!stock.isAttachedToWindow || !visual.isAttachedToWindow) continue
            visual.getLocationInWindow(targetPosition)
            val x = targetPosition[0] - rootPosition[0].toFloat()
            val y = targetPosition[1] - rootPosition[1].toFloat()
            minX = min(minX, x - overscan)
            minY = min(minY, y - overscan)
            maxX = max(maxX, x + visual.width + overscan)
            maxY = max(maxY, y + visual.height + overscan)
        }
        if (!minX.isFinite()) return false
        clip.set(max(0f, minX), max(0f, minY),
            min(width.toFloat(), maxX), min(height.toFloat(), maxY))
        return clip.width() > 0 && clip.height() > 0
    }

    override fun DrawScope.drawPrismalGlass(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        readSamplingState()
        val coords = coordinates ?: return
        if (disabled || !node.hasDisplayList()) return
        try {
            root.getLocationInWindow(rootPosition)
            // Prismal's target coordinates refer to the actual moving glass
            // capsule. Align the sampled native scene to that capsule in window
            // space; never treat the switch's left edge as a fixed source origin.
            val pos = coords.positionInWindow()
            val x = pos.x - rootPosition[0]
            val y = pos.y - rootPosition[1]
            drawIntoCanvas { drawCanvas ->
                val nativeCanvas: Canvas = drawCanvas.nativeCanvas
                if (!nativeCanvas.isHardwareAccelerated) return@drawIntoCanvas
                val save = nativeCanvas.save()
                nativeCanvas.translate(-x, -y)
                nativeCanvas.drawRenderNode(node)
                nativeCanvas.restoreToCount(save)
            }
        } catch (_: Throwable) {
            // Sampling errors must not take down com.android.settings.
            // The material may show its foreground-only fallback for a frame.
        }
    }

    fun release() {
        visibleSwitches.clear()
        node.discardDisplayList()
        dirty = false
    }
}
