package com.hellovoid.liquiddock

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import com.styropyr0.prismal.PrismalBackdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop

/**
 * Adapts the already-recorded MIUIX full-page backdrop to Prismal's sampler.
 *
 * The header's LayerBackdrop records both the solid page underlay and the
 * rendered content (including nested glass controls). Bottom tabs previously
 * sampled a merged pair of Prismal layers, which is not the same scene.
 *
 * Use the exact same LayerBackdrop graphics layer for both bars. This avoids
 * a second full-screen recording, retains MIUIX coordinate adjustment and
 * inverse graphics-layer transforms during Prismal tab press/drag, and leaves
 * the upstream PrismalGlassBottomTabs renderer/gestures unchanged.
 */
@Stable
internal class GuiFullPagePrismalBackdrop(
    private val source: LayerBackdrop,
) : PrismalBackdrop {
    override val isCoordinatesDependent: Boolean = true

    private var sourcePlaced by mutableStateOf(false)

    /** A one-time layout signal so newly mounted Prismal glass can resample. */
    fun onSourcePlaced() {
        if (!sourcePlaced) sourcePlaced = true
    }

    override fun readSamplingState() {
        // Prismal observes this before recording its own glass layer.
        sourcePlaced
    }

    override fun DrawScope.drawPrismalGlass(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        if (!sourcePlaced || coordinates == null) return
        // Delegate MIUIX's own source-coordinate correction and inverse
        // transform, rather than drawing the layer with unadjusted offsets.
        with(source) {
            drawBackdrop(
                density = density,
                coordinates = coordinates,
                layerBlock = layerBlock,
                downscaleFactor = 1,
            )
        }
    }
}
