package com.hellovoid.liquiddock

import androidx.compose.runtime.Stable
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
        if (downscaleFactor <= 1) {
            with(source) {
                drawPrismalGlass(density, coordinates, layerBlock)
            }
        } else {
            withTransform({ scale(1f / downscaleFactor, 1f / downscaleFactor) }) {
                with(source) {
                    drawPrismalGlass(density, coordinates, layerBlock)
                }
            }
        }
    }
}
