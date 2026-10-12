// Portions adapted from PrismalAGSL v1.0.4 by Saurav Sajeev.
// MIT License
// Copyright (c) 2026 Saurav Sajeev
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//
// Local adaptation of PrismalAGSL v1.0.4 PrismalGlassBottomTabs.kt (MIT license).
// Original: https://github.com/styropyr0/PrismalAGSL
// Keeps upstream optical/gesture pipeline intact; separates hit targets,
// invisible glyph sampler, and visible glyphs with moving-droplet masking.
// Do not alter the upstream dependency.
package com.hellovoid.liquiddock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.styropyr0.prismal.components.LocalPrismalBottomTabHighlightedIndex
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.styropyr0.prismal.PrismalBackdrop
import com.styropyr0.prismal.sources.prismalGlassLayer
import com.styropyr0.prismal.sources.rememberPrismalMergedSource
import com.styropyr0.prismal.sources.rememberPrismalGlassLayer
import com.styropyr0.prismal.drawPrismalGlass
import com.styropyr0.prismal.effects.applyPrismalGlassEffects
import com.styropyr0.prismal.effects.prismalLens
import com.styropyr0.prismal.specular.PrismalSpecular
import com.styropyr0.prismal.interactive.PrismalSpringMotion
import com.styropyr0.prismal.interactive.PrismalPressRipple
import com.styropyr0.prismal.depth.PrismalDepthInset
import com.styropyr0.prismal.depth.PrismalDepthShadow
import com.styropyr0.prismal.shapes.PrismalCapsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

// PrismalAGSL's scale local is internal. Keep an equivalent local for this
// app-owned adapter rather than reaching into the dependency's internals.
private val LocalGuiPrismalBottomTabScale = staticCompositionLocalOf { { 1f } }

@Composable
internal fun RowScope.GuiSampledPrismalBottomTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scale = LocalGuiPrismalBottomTabScale.current
    Column(
        modifier
            .clip(PrismalCapsule())
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val value = scale()
                scaleX = value
                scaleY = value
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/**
 * iOS-style glass bottom tab bar with a sliding selection pill and press feedback.
 *
 * @param selectedTabIndex Index of the currently selected tab.
 * @param onTabSelected Called when the user selects a tab.
 * @param backdrop Source sampled through the tab bar glass.
 * @param tabsCount Number of tabs (must match [content] child count).
 * @param content Tab items, typically [PrismalGlassBottomTab] composables.
 * @param tintDropletContent When true, tints tab content sampled by the selection
 *   droplet. Set to false to keep the colors from [content].
 * @param dropletContentTint Color used when [tintDropletContent] is true. When `null`,
 *   defaults to iOS-style blue (`#0088FF` light / `#0091FF` dark). Pass any [Color]
 *   (e.g. [Color.Black]) to override.
 * @param specular The specular setting for the base pill
 */
@Composable
internal fun GuiSampledPrismalBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: PrismalBackdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    adaptiveLuminance: Boolean = false,
    luminance: () -> Float = { 0.5f },
    tintDropletContent: Boolean = true,
    dropletContentTint: Color? = null,
    specular: (() -> PrismalSpecular?)? = { PrismalSpecular.Default },
    hitTargetContent: @Composable RowScope.() -> Unit,
    sampledContent: @Composable RowScope.() -> Unit,
    visibleContent: @Composable RowScope.() -> Unit
) {
    val isLightTheme = !isSystemInDarkTheme()
    val effectiveDropletTint = dropletContentTint ?: if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)
    val containerColor = if (isLightTheme) Color(0xFFFAFAFA).copy(0.4f)
    else Color(0xFF121212).copy(0.4f)

    val tabsBackdrop = rememberPrismalGlassLayer()
    val dropletBackdrop = rememberPrismalMergedSource(backdrop, tabsBackdrop)

    tabsBackdrop.readSamplingState()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8.dp.toPx()) / tabsCount
        }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember(selectedTabIndex) {
            mutableIntStateOf(selectedTabIndex())
        }
        var dragHighlightIndex by remember { mutableStateOf<Int?>(null) }
        val highlightedTabIndex = dragHighlightIndex ?: selectedTabIndex()
        val dampedDragAnimation = remember(animationScope, tabsCount, tabWidth, isLtr) {
            PrismalSpringMotion(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {
                    dragHighlightIndex = value.fastRoundToInt().coerceIn(0, tabsCount - 1)
                },
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().coerceIn(0, tabsCount - 1)
                    dragHighlightIndex = targetIndex
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            0f,
                            spring(1f, 300f, 0.5f)
                        )
                    }
                },
                onDrag = { _, dragAmount ->
                    val nextValue =
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f).fastCoerceIn(
                            0f,
                            (tabsCount - 1).toFloat()
                        )
                    updateValue(nextValue)
                    dragHighlightIndex = nextValue.fastRoundToInt()
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }
        LaunchedEffect(selectedTabIndex) {
            snapshotFlow { selectedTabIndex() }
                .collectLatest { index ->
                    currentIndex = index
                    dragHighlightIndex = null
                }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                    onTabSelected(index)
                }
        }

        val interactivePrismalSpecular = remember(animationScope, tabWidth, isLtr) {
            PrismalPressRipple(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        CompositionLocalProvider(
            LocalGuiPrismalBottomTabScale provides {
                lerp(1f, 1.05f, dampedDragAnimation.pressProgress)
            },
            LocalPrismalBottomTabHighlightedIndex provides { highlightedTabIndex }
        ) {
            Row(
                Modifier
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .drawPrismalGlass(
                        backdrop = backdrop,
                        shape = { PrismalCapsule() },
                        specular = specular,
                        effects = {
                            applyPrismalGlassEffects(
                                density = density,
                                adaptiveLuminance = adaptiveLuminance,
                                luminance = luminance(),
                                blurRadiusPx = with(density) { 8.dp.toPx() },
                                refractionHeightPx = with(density) { 24.dp.toPx() },
                                refractionAmountPx = with(density) { 24.dp.toPx() }
                            )
                        },
                        layerBlock = {
                            val progress = dampedDragAnimation.pressProgress
                            val scale = lerp(1f, 1f + 16.dp.toPx() / size.width, progress)
                            scaleX = scale
                            scaleY = scale
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactivePrismalSpecular.modifier)
                    .height(64.dp)
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = hitTargetContent
            )

            // Draw a single crisp tab glyph pass OUTSIDE the moving droplet.
            // The droplet itself displays the glyphs refracted from tabsBackdrop.
            // Masking the overlapping pixels avoids rendering raw + refracted
            // glyphs simultaneously (the historic long-press double text).
            Row(
                Modifier
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .height(64.dp)
                    .fillMaxWidth()
                    .padding(4.dp)
                    .drawWithContent {
                        val velocity = dampedDragAnimation.velocity / 10f
                        val scaleX = dampedDragAnimation.scaleX /
                            (1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f))
                        val centerX = (dampedDragAnimation.value + 0.5f) * tabWidth
                        val halfWidth = tabWidth * scaleX / 2f
                        clipRect(
                            left = centerX - halfWidth,
                            top = 0f,
                            right = centerX + halfWidth,
                            bottom = size.height,
                            clipOp = ClipOp.Difference
                        ) {
                            drawContent()
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                content = visibleContent
            )

            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .prismalGlassLayer(tabsBackdrop)
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .drawPrismalGlass(
                        backdrop = backdrop,
                        shape = { PrismalCapsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            applyPrismalGlassEffects(
                                density = density,
                                adaptiveLuminance = adaptiveLuminance,
                                luminance = luminance(),
                                blurRadiusPx = with(density) { 8.dp.toPx() },
                                refractionHeightPx = with(density) { 24.dp.toPx() } * progress,
                                refractionAmountPx = with(density) { 24.dp.toPx() } * progress
                            )
                        },
                        specular = {
                            val progress = dampedDragAnimation.pressProgress
                            PrismalSpecular.Default.copy(alpha = progress)
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactivePrismalSpecular.modifier)
                    .height(56.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .then(
                        if (tintDropletContent) {
                            Modifier.drawWithContent {
                                val paint = Paint().apply {
                                    colorFilter = ColorFilter.tint(effectiveDropletTint)
                                }
                                drawContext.canvas.saveLayer(
                                    Rect(0f, 0f, size.width, size.height),
                                    paint
                                )
                                drawContent()
                                drawContext.canvas.restore()
                            }
                        } else {
                            Modifier
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically,
                content = sampledContent
            )
        }

        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .then(interactivePrismalSpecular.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawPrismalGlass(
                    backdrop = dropletBackdrop,
                    shape = { PrismalCapsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        prismalLens(
                            refractionHeight = with(density) { 10.dp.toPx() } * progress,
                            refractionAmount = with(density) { 14.dp.toPx() } * progress,
                            chromaticAberration = 1f
                        )
                    },
                    specular = {
                        val progress = dampedDragAnimation.pressProgress
                        PrismalSpecular.Default.copy(alpha = progress)
                    },
                    depthShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        PrismalDepthShadow(alpha = progress)
                    },
                    depthInset = {
                        val progress = dampedDragAnimation.pressProgress
                        PrismalDepthInset(
                            radius = 8.dp * progress,
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
                        drawRect(
                            if (isLightTheme) Color.Black.copy(0.1f)
                            else Color.White.copy(0.1f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(56.dp)
                .fillMaxWidth(1f / tabsCount)
        )
    }
}
