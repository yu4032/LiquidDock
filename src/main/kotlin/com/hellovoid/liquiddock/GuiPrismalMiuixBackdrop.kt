package com.hellovoid.liquiddock

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import com.styropyr0.prismal.PrismalBackdrop
import top.yukonga.miuix.kmp.blur.Backdrop

/**
 * Read-only bridge: MIUIX header blur samples the SAME Prismal backdrop
 * as the bottom navigation and all existing Prismal overlays.
 *
 * No LayerBackdrop/GraphicsLayer recording is added; in particular the
 * MIUIX blur's source is not a second full-screen copy of the page.
 */
@Stable
internal class GuiPrismalMiuixBackdrop(
    private val source: PrismalBackdrop,
) : Backdrop {
    override val isCoordinatesDependent: Boolean
        get() = source.isCoordinatesDependent

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Int,
    ) {
        // MIUIX's textureEffect records into a downscaled output buffer.
        // Prismal source coordinates remain in physical page pixels.
        // DrawBackdropModifier records the source at a size reduced by
        // downscaleFactor and supplies layout coordinates in full-resolution
        // pixels. Compose's default scale pivot is the DrawScope CENTER: using
        // it here shifts the sampled source across the header.
        //
        // PrismalBackdrop already translates its source by the consumer-to-
        // layer offset; scaling around the top-left origin then preserves
        // exactly the screen-space coordinates that MIUIX's textureBlur expects.
        source.readSamplingState()
        if (downscaleFactor <= 1) {
            with(source) {
                drawPrismalGlass(density, coordinates, layerBlock)
            }
        } else {
            val invScale = 1f / downscaleFactor.toFloat()
            withTransform({
                scale(invScale, invScale, pivot = Offset.Zero)
            }) {
                with(source) {
                    drawPrismalGlass(density, coordinates, layerBlock)
                }
            }
        }
    }
}
