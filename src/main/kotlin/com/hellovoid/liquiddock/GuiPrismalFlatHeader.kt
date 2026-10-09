package com.hellovoid.liquiddock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styropyr0.prismal.PrismalBackdrop
import com.styropyr0.prismal.depth.PrismalDepthShadow
import com.styropyr0.prismal.drawPrismalGlass
import com.styropyr0.prismal.effects.applyPrismalGlassEffects
import com.styropyr0.prismal.specular.PrismalSpecular

/**
 * Flat translucent header with the original Prismal optics.
 *
 * The glass slab extends beyond the screen's top and horizontal edges so
 * its specular rim/shadow are offscreen. Foreground MIUIX controls keep their
 * original measured placement, and the only visible edge is the bottom line.
 * An opaque theme-color cap eases into the blurred status bar from the top.
 */
@Composable
internal fun GuiPrismalFlatHeader(
    backdrop: PrismalBackdrop,
    modifier: Modifier,
    blurRadius: Dp,
    overlayColor: Color,
    statusBarEdgeColor: Color,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Layout(
        modifier = modifier,
        content = {
            Box(
                modifier = Modifier.drawPrismalGlass(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    specular = { PrismalSpecular.Default },
                    depthShadow = { PrismalDepthShadow.Default },
                    effects = {
                        applyPrismalGlassEffects(
                            density = density,
                            adaptiveLuminance = false,
                            luminance = 0.5f,
                            blurRadiusPx = with(density) { blurRadius.toPx() },
                            refractionHeightPx = 0f,
                            refractionAmountPx = 0f,
                            brightness = 0f,
                            saturation = 1.35f,
                            depthEffect = false,
                            chromaticAberration = 0f,
                            useVibrancy = false,
                        )
                    },
                    onDrawSurface = { drawRect(overlayColor) },
                ),
            )
            Box {
                if (statusBarHeight > 0.dp) {
                    // The overscanned glass fades near the physical upper edge.
                    // Cap only the upper status-bar region with a theme-neutral
                    // opaque color; fade out before the app-bar title area.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(statusBarHeight + 12.dp)
                            .background(
                                Brush.verticalGradient(
                                    0f to statusBarEdgeColor,
                                    0.28f to statusBarEdgeColor,
                                    1f to statusBarEdgeColor.copy(alpha = 0f),
                                ),
                            ),
                    )
                }
                content()
            }
        },
    ) { measurables, constraints ->
        // A glass-only overscan: never offset the title, actions or the
        // explicit bottom divider. Avoid adding a separate blur/capture layer.
        val foreground = measurables[1].measure(constraints)
        val overscanPx = 24.dp.roundToPx()
        val glass = measurables[0].measure(
            Constraints.fixed(
                foreground.width + overscanPx * 2,
                foreground.height + overscanPx,
            ),
        )
        layout(foreground.width, foreground.height) {
            glass.place(-overscanPx, -overscanPx)
            foreground.place(0, 0)
        }
    }
}
