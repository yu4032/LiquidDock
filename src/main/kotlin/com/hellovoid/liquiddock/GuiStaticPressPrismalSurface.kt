package com.hellovoid.liquiddock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.styropyr0.prismal.PrismalBackdrop
import com.styropyr0.prismal.drawPrismalGlass
import com.styropyr0.prismal.drawPrismalGlassTint
import com.styropyr0.prismal.effects.applyPrismalGlassEffects
import com.styropyr0.prismal.interactive.PrismalPressRipple

/**
 * Settings Cell variant of PrismalGlassSurface (PrismalAGSL v1.0.4, MIT).
 *
 * Keeps the native glass optics while omitting the depth shadow that would
 * be sampled again by the bottom capsule. For clickable navigation Cells,
 * retains the AGSL press ripple without the scale/parallax layerBlock.
 * Noninteractive Cells have no click listener or press ripple.
 */
@Composable
internal fun GuiStaticPressPrismalSurface(
    backdrop: PrismalBackdrop,
    modifier: Modifier,
    shape: () -> Shape,
    onClick: (() -> Unit)?,
    blurRadius: Dp,
    tint: Color,
    tintAlpha: Float,
    saturation: Float,
    refractionHeightPx: Float,
    refractionAmountPx: Float,
    chromaticAberration: Float,
    depthEffect: Boolean,
    surfaceColor: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val animationScope = rememberCoroutineScope()
    val pressRipple = remember(animationScope, onClick != null) {
        if (onClick != null) PrismalPressRipple(animationScope = animationScope) else null
    }

    Box(
        modifier = modifier
            .drawPrismalGlass(
                backdrop = backdrop,
                shape = shape,
                // Default PrismalDepthShadow is re-captured into the bottom
                // capsule and its soft gradient shows visible color stepping
                // after a second blur/refraction pass. Preserve the specular
                // edge, native lens, tint and outline; skip only this shadow.
                depthShadow = null,
                effects = {
                    applyPrismalGlassEffects(
                        density = density,
                        adaptiveLuminance = false,
                        luminance = 0.5f,
                        blurRadiusPx = with(density) { blurRadius.toPx() },
                        refractionHeightPx = refractionHeightPx,
                        refractionAmountPx = refractionAmountPx,
                        brightness = 0f,
                        saturation = saturation,
                        depthEffect = depthEffect,
                        chromaticAberration = chromaticAberration,
                        useVibrancy = true,
                    )
                },
                // No layerBlock: keep scale=1 and translation=0 during both
                // press and drag, while retaining the identical ripple shader.
                onDrawSurface = {
                    drawPrismalGlassTint(tint, tintAlpha)
                    if (surfaceColor.isSpecified) drawRect(surfaceColor)
                },
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = null,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else Modifier,
            )
            .then(pressRipple?.modifier ?: Modifier)
            .then(pressRipple?.gestureModifier ?: Modifier),
        content = content,
    )
}
