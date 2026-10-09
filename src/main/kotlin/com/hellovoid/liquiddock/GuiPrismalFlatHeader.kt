package com.hellovoid.liquiddock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import com.styropyr0.prismal.PrismalBackdrop
import com.styropyr0.prismal.drawPrismalGlass
import com.styropyr0.prismal.effects.applyPrismalGlassEffects

/**
 * Uniform header blur drawn entirely by PrismalAGSL.
 *
 * Uses the same background + screen Prismal source as the bottom glass tabs.
 * Unlike the default interactive PrismalGlassSurface, this static header has
 * no lens zone, hue tint, press geometry or touch interception. The original
 * SmallTopAppBar and its independent glass buttons remain the foreground.
 *
 * Standard Compose text/cards inside the scaffold's captured body are already
 * recorded by prismalGlassLayer; they do not need to become Prismal widgets.
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
