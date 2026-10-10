package com.hellovoid.liquiddock

// Local PrismalAGSL v1.0.4 gesture-preserving adaptation: sample ONLY on touch.
import com.styropyr0.prismal.components.LocalPrismalParentGlassLayer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.styropyr0.prismal.shapes.PrismalCapsule
import com.styropyr0.prismal.PrismalBackdrop
import com.styropyr0.prismal.sources.prismalGlassLayer
import com.styropyr0.prismal.sources.rememberPrismalWrappedSource
import com.styropyr0.prismal.sources.rememberPrismalMergedSource
import com.styropyr0.prismal.sources.rememberPrismalGlassLayer
import com.styropyr0.prismal.drawPrismalGlass
import com.styropyr0.prismal.effects.applyPrismalGlassEffects
import com.styropyr0.prismal.effects.prismalBlur
import com.styropyr0.prismal.effects.prismalLens
import com.styropyr0.prismal.specular.PrismalSpecular
import com.styropyr0.prismal.interactive.PrismalSpringMotion
import com.styropyr0.prismal.depth.PrismalDepthInset
import com.styropyr0.prismal.depth.PrismalDepthShadow
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

/**
 * Glass-styled slider with a refracting thumb and damped drag physics.
 *
 * @param value Current value provider (read on each frame during drag).
 * @param onValueChange Called when the value changes.
 * @param valueRange Allowed value range.
 * @param visibilityThreshold Minimum change delta before [onValueChange] fires.
 * @param backdrop Source sampled through the track and thumb glass.
 */
@Composable
internal fun GuiOnTouchPrismalSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: PrismalBackdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    adaptiveLuminance: Boolean = false,
    luminance: () -> Float = { 0.5f },
    steps: Int = 0,
    snapIncrement: Float = 0f,
) {
    val density = LocalDensity.current
    val isLightTheme = !isSystemInDarkTheme()
    val accentColor =
        if (isLightTheme) Color(0xFF0088FF)
        else Color(0xFF0091FF)
    val trackColor =
        if (isLightTheme) Color(0xFF787878).copy(0.2f)
        else Color(0xFF787880).copy(0.36f)

    val trackBackdrop = rememberPrismalGlassLayer()
    val parentGlassLayer = LocalPrismalParentGlassLayer.current
    val underlayBackdrop = parentGlassLayer ?: backdrop


    BoxWithConstraints(
        modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = constraints.maxWidth
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var didDrag by remember { mutableStateOf(false) }
        var isDragging by remember { mutableStateOf(false) }
        var isTrackPressed by remember { mutableStateOf(false) }
        val sampling = enabled && (isDragging || isTrackPressed)
        var lastReportedValue by remember { mutableFloatStateOf(value()) }
        // Keep raw drag displacement between pointer events. Re-applying each small
        // delta to an already-snapped target would make discrete thumbs stick.
        var rawDragTarget by remember { mutableFloatStateOf(value()) }
        val trackWidthState = remember { mutableIntStateOf(0) }
        trackWidthState.intValue = trackWidth
        val dampedDragAnimation = remember(
            animationScope,
            valueRange.start,
            valueRange.endInclusive,
            steps,
            snapIncrement,
        ) {
            PrismalSpringMotion(
                animationScope = animationScope,
                initialValue = value(),
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = {
                    isDragging = true
                    didDrag = false
                    rawDragTarget = targetValue
                },
                onDragStopped = {
                    isDragging = false
                    if (didDrag) {
                        lastReportedValue = targetValue
                        onValueChange(targetValue)
                    }
                },
                onDrag = { _, dragAmount ->
                    val width = trackWidthState.intValue
                    if (width == 0) return@PrismalSpringMotion
                    if (!didDrag) {
                        didDrag = dragAmount.x != 0f
                    }
                    val delta =
                        (valueRange.endInclusive - valueRange.start) * (dragAmount.x / width.toFloat())
                    val rawBase = if (steps > 0 || snapIncrement > 0f) rawDragTarget else targetValue
                    val rawNext = (
                            if (isLtr) rawBase + delta
                            else rawBase - delta
                            ).fastCoerceIn(valueRange.start, valueRange.endInclusive)
                    if (steps > 0 || snapIncrement > 0f) rawDragTarget = rawNext
                    val nextValue = DiscreteSliderSteps.snap(
                        rawNext, valueRange.start, valueRange.endInclusive, steps, snapIncrement,
                    )
                    updateValue(nextValue)
                    val shouldReport = if (steps > 0 || snapIncrement > 0f) {
                        nextValue != lastReportedValue
                    } else {
                        abs(nextValue - lastReportedValue) >= visibilityThreshold
                    }
                    if (shouldReport) {
                        lastReportedValue = nextValue
                        onValueChange(nextValue)
                    }
                }
            )
        }
        var motionFrame by remember { mutableIntStateOf(0) }
        // The expensive motion-driven glass pass is completely dormant
        // between gestures. Keep motion physics alive for value animations.
        LaunchedEffect(dampedDragAnimation, sampling) {
            if (sampling) {
                snapshotFlow { dampedDragAnimation.animationSnapshot() }
                    .collect { motionFrame++ }
            }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { value() }
                .collectLatest { current ->
                    lastReportedValue = current
                    if (!isDragging && dampedDragAnimation.targetValue != current) {
                        dampedDragAnimation.updateValue(current)
                    }
                }
        }

        // A blocked widget preflight disables the row/column slider while waiting.
        // Immediately return its thumb to the last committed value, not the
        // tentative 2/3-column stop that has not passed safety validation.
        LaunchedEffect(enabled, dampedDragAnimation, steps) {
            if (!enabled && (steps > 0 || snapIncrement > 0f)) {
                dampedDragAnimation.updateValue(value())
            }
        }

        // No track GraphicsLayer recording while the thumb is idle.
        if (sampling) {
            trackBackdrop.readSamplingState()
            parentGlassLayer?.readSamplingState()
        }
        Box(Modifier.then(if (sampling) Modifier.prismalGlassLayer(trackBackdrop) else Modifier)) {
            Box(
                Modifier
                    .clip(PrismalCapsule())
                    .background(trackColor)
                    .then(if (enabled) Modifier.pointerInput(animationScope, trackWidth, valueRange, isLtr) {
                        detectTapGestures(
                            onPress = {
                                isTrackPressed = true
                                try { tryAwaitRelease() } finally { isTrackPressed = false }
                            },
                            onTap = { position ->
                            val delta =
                                (valueRange.endInclusive - valueRange.start) * (position.x / trackWidth)
                            val targetValue =
                                (if (isLtr) valueRange.start + delta
                                else valueRange.endInclusive - delta)
                                    .coerceIn(valueRange)
                            val snapped = DiscreteSliderSteps.snap(
                                targetValue, valueRange.start, valueRange.endInclusive, steps, snapIncrement,
                            )
                            dampedDragAnimation.animateToValue(snapped)
                            onValueChange(snapped)
                            },
                        )
                    } else Modifier)
                    .height(6.dp)
                    .fillMaxWidth()
            )

            Box(
                Modifier
                    .clip(PrismalCapsule())
                    .background(accentColor)
                    .height(6.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val width =
                            (constraints.maxWidth * dampedDragAnimation.progress).fastRoundToInt()
                        layout(width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    }
            )
        }

        if (motionFrame >= 0) {
            Box(
                Modifier
                    .graphicsLayer {
                        translationX =
                            (-size.width / 2f + trackWidth * dampedDragAnimation.progress)
                                .fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) *
                                    if (isLtr) 1f else -1f
                    }
                    .then(if (enabled) dampedDragAnimation.modifier else Modifier)
                    .then(if (sampling) Modifier.drawPrismalGlass(
                        backdrop = rememberPrismalMergedSource(
                            underlayBackdrop,
                            rememberPrismalWrappedSource(trackBackdrop) { drawPrismalGlass ->
                                val progress = dampedDragAnimation.pressProgress
                                val scaleX = lerp(2f / 3f, 1f, progress)
                                val scaleY = lerp(0f, 1f, progress)
                                scale(scaleX, scaleY) {
                                    drawPrismalGlass()
                                }
                            }
                        ),
                        shape = { PrismalCapsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            if (adaptiveLuminance) {
                                applyPrismalGlassEffects(
                                    density = density,
                                    adaptiveLuminance = true,
                                    luminance = luminance(),
                                    blurRadiusPx = with(density) { 8.dp.toPx() } * (1f - progress),
                                    refractionHeightPx = with(density) { 10.dp.toPx() } * progress,
                                    refractionAmountPx = with(density) { 14.dp.toPx() } * progress,
                                    chromaticAberration = 1f
                                )
                            } else {
                                prismalBlur(with(density) { 8.dp.toPx() } * (1f - progress))
                                prismalLens(
                                    refractionHeight = with(density) { 10.dp.toPx() } * progress,
                                    refractionAmount = with(density) { 14.dp.toPx() } * progress,
                                    chromaticAberration = 1f
                                )
                            }
                        },
                        specular = {
                            val progress = dampedDragAnimation.pressProgress
                            PrismalSpecular.Ambient.copy(
                                width = PrismalSpecular.Ambient.width / 1.5f,
                                blurRadius = PrismalSpecular.Ambient.blurRadius / 1.5f,
                                alpha = progress
                            )
                        },
                        depthShadow = {
                            PrismalDepthShadow(
                                radius = 4.dp,
                                color = Color.Black.copy(alpha = 0.05f)
                            )
                        },
                        depthInset = {
                            val progress = dampedDragAnimation.pressProgress
                            PrismalDepthInset(
                                radius = 4.dp * progress,
                                alpha = progress
                            )
                        },
                        layerBlock = {
                            scaleX = dampedDragAnimation.scaleX
                            scaleY = dampedDragAnimation.scaleY
                            val velocity = dampedDragAnimation.velocity / 10f
                            scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            val progress = dampedDragAnimation.pressProgress
                            drawRect(Color.White.copy(alpha = 1f - progress))
                        }
                    ) else Modifier
                    )
                    .size(40.dp, 24.dp)
            ) {
                if (!sampling) {
                    // Frozen native Prismal thumb, never a flat white substitute.
                    // Gestures stay on the stable outer Box.
                    GuiFrozenPrismalChrome(
                        backdrop = underlayBackdrop,
                        shape = { PrismalCapsule() },
                        modifier = Modifier.matchParentSize(),
                        blurRadius = 8.dp,
                        refractionHeightPx = with(density) { 4.dp.toPx() },
                        refractionAmountPx = with(density) { 6.dp.toPx() },
                        chromaticAberration = 0.28f,
                        surfaceColor = Color.White.copy(alpha = 0.75f),
                        depthEffect = false,
                    )
                }
            }
        }
    }
}
