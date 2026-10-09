package com.hellovoid.liquiddock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 * Page-entry Cell variant of PrismalGlassSurface (PrismalAGSL v1.0.4, MIT).
 *
 * Match Prismal's glass optics, specular/depth defaults, click semantics and
 * animated AGSL press ripple. Deliberately omit only its interactive layerBlock,
 * which scales the entire Cell and translates it along the pointer. In particular,
 * DO NOT remove PrismalPressRipple: its flash/highlight is the expected click effect.
 *
 * Scoped to ModernFeatureCard. Switch/slider/thumb and all other surfaces
 * retain their unmodified upstream animations.
 */
@Composable
internal fun GuiStaticPressPrismalSurface(
    backdrop: PrismalBackdrop,
    modifier: Modifier,
    shape: () -> Shape,
    onClick: () -> Unit,
    blurRadius: Dp,
    tint: Color,
    tintAlpha: Float,
    saturation: Float,
    refractionHeightPx: Float,
    refractionAmountPx: Float,
    chromaticAberration: Float,
    depthEffect: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val animationScope = rememberCoroutineScope()
    val pressRipple = remember(animationScope) {
        PrismalPressRipple(animationScope = animationScope)
    }

    Box(
        modifier = modifier
            .drawPrismalGlass(
                backdrop = backdrop,
                shape = shape,
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
                },
            )
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .then(pressRipple.modifier)
            .then(pressRipple.gestureModifier),
        content = content,
    )
}
