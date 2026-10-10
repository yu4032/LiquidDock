package com.hellovoid.liquiddock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.styropyr0.prismal.components.LocalPrismalBottomTabHighlightedIndex
import com.styropyr0.prismal.components.PrismalGlassBottomTab
import com.styropyr0.prismal.components.PrismalGlassBottomTabs
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Intentional single-pass labels preserve the existing no-ghost visual contract.
// Native Prismal tab optics and gestures still render; the original tab content
// is empty to avoid double-refracted glyphs on long-press (see audit docs).
@Composable
internal fun ModernBottomNavigation(
    labels: List<String>,
    icons: List<ImageVector>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    val backdrop = LocalPrismalOverlayBackdrop.current
    val selected by rememberUpdatedState(selectedIndex)
    val onSelect by rememberUpdatedState(onSelected)
    // Read the upstream drag candidate from inside the actual native tab
    // CompositionLocal. Outside siblings cannot observe that local directly.
    // Report only the index, never duplicate glyphs into the hidden sampler.
    val highlightedIndex = remember { mutableIntStateOf(selectedIndex) }
    LaunchedEffect(selectedIndex) {
        highlightedIndex.intValue = selectedIndex
    }
    val stableSelectedIndex = remember { { selected } }
    val stableTabChange: (Int) -> Unit = remember {
        { index -> if (index != selected) onSelect(index) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .height(64.dp),
        ) {
            if (backdrop != null) {
                PrismalGlassBottomTabs(
                    selectedTabIndex = stableSelectedIndex,
                    onTabSelected = stableTabChange,
                    backdrop = backdrop,
                    tabsCount = labels.size,
                    modifier = Modifier.fillMaxSize(),
                    tintDropletContent = false,
                ) {
                    // Prismal renders tab content twice: once visibly and once in a
                    // hidden recording layer for the droplet lens. Rendering text
                    // there creates a second, refracted copy while long-pressing.
                    // Keep the native capsule hit targets / spring gestures empty.
                    labels.indices.forEach { index ->
                        PrismalGlassBottomTab(
                            onClick = {
                                if (index != selected) onSelect(index)
                            },
                        ) {
                            val highlighted = LocalPrismalBottomTabHighlightedIndex.current
                            val currentCandidate = highlighted().coerceIn(0, labels.lastIndex)
                            SideEffect {
                                if (highlightedIndex.intValue != currentCandidate) {
                                    highlightedIndex.intValue = currentCandidate
                                }
                            }
                        }
                    }
                }
                // Draw labels and icons once above the droplet, without a second
                // pointer target: touches still reach Prismal's capsule tabs.
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    labels.forEachIndexed { index, label ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(
                                2.dp, Alignment.CenterVertically,
                            ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ModernTabContents(
                                label = label,
                                icon = icons[index],
                                active = index == highlightedIndex.intValue,
                                draggingCandidate = highlightedIndex.intValue != selected &&
                                    index == highlightedIndex.intValue,
                            )
                        }
                    }
                }
            } else {
                val fallbackShape = RoundedCornerShape(30.dp)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(fallbackShape)
                        .background(
                            color = MiuixTheme.colorScheme.surface.copy(alpha = 0.96f),
                            shape = fallbackShape,
                        )
                        .border(
                            width = 1.dp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                            shape = fallbackShape,
                        )
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    labels.forEachIndexed { index, label ->
                        val active = index == selected
                        val shape = RoundedCornerShape(28.dp)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(shape)
                                .background(
                                    if (active) MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else Color.Transparent,
                                    shape = shape,
                                )
                                .clickable(
                                    interactionSource = null,
                                    indication = null,
                                ) {
                                    if (index != selected) onSelect(index)
                                },
                            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ModernTabContents(label, icons[index], active)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernTabContents(
    label: String,
    icon: ImageVector,
    active: Boolean,
    draggingCandidate: Boolean = false,
) {
    val contentColor by animateColorAsState(
        targetValue = if (active) {
            MiuixTheme.colorScheme.primary
        } else {
            MiuixTheme.colorScheme.onSurface.copy(alpha = 0.64f)
        },
        animationSpec = tween(130),
        label = "bottom-tab-glyph-color",
    )
    val contentScale by animateFloatAsState(
        targetValue = if (draggingCandidate) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "bottom-tab-glyph-scale",
    )
    Icon(
        imageVector = icon,
        contentDescription = label,
        tint = contentColor,
        modifier = Modifier
            .size(21.dp)
            .graphicsLayer { scaleX = contentScale; scaleY = contentScale },
    )
    Text(
        text = label,
        color = contentColor,
        fontSize = 11.sp,
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.graphicsLayer {
            scaleX = contentScale
            scaleY = contentScale
        },
    )
}

