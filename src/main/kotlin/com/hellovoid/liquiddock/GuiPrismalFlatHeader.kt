package com.hellovoid.liquiddock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.styropyr0.prismal.PrismalBackdrop
import com.styropyr0.prismal.drawPrismalGlass
import com.styropyr0.prismal.effects.applyPrismalGlassEffects

/**
 * Flat Prismal header that samples the captured background and page content.
 * No rim, drop shadow, refraction or hue shift: the settings Scaffold
 * draws its only visible boundary as a thin bottom divider. MIUIX owns the
 * foreground app bar and buttons retain their own Prismal glass effects.
 */
@Composable
internal fun GuiPrismalFlatHeader(
    backdrop: PrismalBackdrop,
    modifier: Modifier,
    blurRadius: Dp,
    overlayColor: Color,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    Box(
        modifier = modifier.drawPrismalGlass(
            backdrop = backdrop,
            shape = { RectangleShape },
            // Prismal's defaults emit a highlight rim and drop shadow on
            // all four edges, including the screen's top and sides.
            // This header should have ONLY the explicit bottom divider.
            specular = null,
            depthShadow = null,
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
                    // Preserve sampled colors: generic PrismalGlassSurface
                    // enables a vibrancy shader that changes hue/saturation.
                    useVibrancy = false,
                )
            },
            onDrawSurface = {
                drawRect(overlayColor)
            },
        ),
        content = content,
    )
}
