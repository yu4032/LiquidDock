package com.hellovoid.liquiddock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import com.styropyr0.prismal.PrismalBackdrop
import com.styropyr0.prismal.PrismalGlassSurface

/**
 * Uses the real Prismal material exactly once per stable geometry/theme.
 *
 * The recorded GPU layer contains ONLY the optical surface. Never place text,
 * toggles, sliders, click handlers or live settings in this cache: those must
 * update independently while the list scrolls. Draw-layer replay does not
 * trigger a second background capture or re-record the expensive lens pass.
 *
 * No PixelCopy, Bitmap, screenshot, or additional full-screen recorder.
 */
private class FrozenPrismalRecord {
    var size: IntSize = IntSize.Zero
    var fingerprint: Int = 0
    var valid: Boolean = false
}

@Composable
internal fun GuiFrozenPrismalChrome(
    backdrop: PrismalBackdrop,
    shape: () -> Shape,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 12.dp,
    refractionHeightPx: Float = 16f,
    refractionAmountPx: Float = 21f,
    chromaticAberration: Float = 0.28f,
    tint: Color = Color.Unspecified,
    tintAlpha: Float = 0.24f,
    surfaceColor: Color = Color.Unspecified,
    saturation: Float = 1.32f,
    depthEffect: Boolean = true,
    invalidateKey: Any? = null,
) {
    val layer = rememberGraphicsLayer()
    val record = remember { FrozenPrismalRecord() }
    // Hard-code material parameters for the life of this draw cache. Re-record
    // only after the target dimensions or explicitly named colors/style change.
    val signature = listOf(
        blurRadius, refractionHeightPx, refractionAmountPx,
        chromaticAberration, tint, tintAlpha, surfaceColor,
        saturation, depthEffect, invalidateKey,
    ).hashCode()

    Box(
        modifier = modifier.drawWithContent {
            val dimensions = size.toIntSize()
            if (dimensions.width <= 0 || dimensions.height <= 0) {
                drawContent()
            } else {
                if (!record.valid || record.size != dimensions ||
                    record.fingerprint != signature) {
                    layer.record(dimensions) {
                        this@drawWithContent.drawContent()
                    }
                    record.size = dimensions
                    record.fingerprint = signature
                    record.valid = true
                }
                drawLayer(layer)
            }
        },
    ) {
        PrismalGlassSurface(
            backdrop = backdrop,
            modifier = Modifier.fillMaxSize(),
            shape = shape,
            onClick = null,
            blurRadius = blurRadius,
            refractionHeightPx = refractionHeightPx,
            refractionAmountPx = refractionAmountPx,
            chromaticAberration = chromaticAberration,
            tint = tint,
            tintAlpha = tintAlpha,
            surfaceColor = surfaceColor,
            saturation = saturation,
            depthEffect = depthEffect,
        )
    }
}
